package project.aoop.space_sandbox.network;

import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.entity.Bullet;
import project.aoop.space_sandbox.entity.Moon;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.RemoteShip;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.physics.OrbitalMechanics;
import project.aoop.space_sandbox.service.CombatService;
import project.aoop.space_sandbox.service.LevelService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Two-player co-op over TCP on the local network.
 * Every player simulates their OWN ship; the network just tells the other side what happened.
 * Game objects are only touched on the JavaFX thread; the network threads only move text.
 */
@Service
public class NetworkService {

    public enum Mode { OFFLINE, HOST, CLIENT }

    public static final int PORT = 9090;

    private static final int    PROTOCOL            = 1;
    private static final long   TIMEOUT_NANOS       = 6_000_000_000L;   // silence for 6 s = gone
    private static final int    TICK_MS             = 50;               // 20 updates per second
    private static final double PLANET_SYNC_SECONDS = 0.5;
    private static final double CLIENT_SPAWN_OFFSET = 7.0;              // world units beside the host

    /** Immutable copy of the local ship: the safe way to hand it to another thread. */
    private record ShipSnapshot(double x, double y, double vx, double vy,
                                double angle, int health, boolean thrusting) {}

    // game objects: JavaFX thread only
    private final CombatService    combatService;
    private final LevelService     levelService;
    private final RemoteShip       remote;
    private final OrbitalMechanics orbitalMechanics;
    private final List<Planet>     planets;

    // shared between threads
    private final Object                lock      = new Object();   // guards the session fields below
    private final Object                writeLock = new Object();   // one writer at a time
    private final BlockingQueue<String> outbound  = new LinkedBlockingQueue<>();
    private final Queue<Msg>            inbound   = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean         closed    = new AtomicBoolean(true);

    private volatile Mode             mode            = Mode.OFFLINE;
    private volatile boolean          connected       = false;
    private volatile long             lastRxNanos;
    private volatile ShipSnapshot     localSnap;
    private volatile int              pendingLevel    = -1;
    private volatile int              hostLevelMirror = 1;
    private volatile String           roomCode        = "";
    private volatile Consumer<String> noticeListener;

    // current session (guarded by lock)
    private ExecutorService          pool;
    private ScheduledExecutorService ticker;
    private ServerSocket             serverSocket;
    private Socket                   socket;
    private BufferedReader           in;
    private BufferedWriter           out;
    private int                      tickCount;      // ticker thread only

    // JavaFX-thread-only state
    private Ship    localShip;
    private boolean placed;
    private int     lastSentLevel;
    private double  planetTimer;

    public NetworkService(CombatService combatService, LevelService levelService,
                          RemoteShip remote, OrbitalMechanics orbitalMechanics,
                          List<Planet> planets) {
        this.combatService    = combatService;
        this.levelService     = levelService;
        this.remote           = remote;
        this.orbitalMechanics = orbitalMechanics;
        this.planets          = planets;
        combatService.setFireListener(this::onLocalFire);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Hosting and joining (call from the JavaFX thread)
    // ════════════════════════════════════════════════════════════════════

    public void hostCoop(Consumer<String> onWaiting, Runnable onPartnerJoined,
                         Consumer<String> onError) {
        if (mode != Mode.OFFLINE) { onError.accept("A co-op session is already open."); return; }

        String ip = LanAddress.find();
        if (ip == null) {
            onError.accept("No network found. Connect to Wi-Fi (or a hotspot) first.");
            return;
        }

        openSession(Mode.HOST);
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(PORT));
            synchronized (lock) { serverSocket = ss; }
        } catch (IOException e) {
            shutdownSession();
            onError.accept("Could not open port " + PORT + " (is another copy already hosting?).");
            return;
        }

        roomCode = RoomCode.fromIp(ip);
        onWaiting.accept(roomCode);

        ExecutorService p;
        synchronized (lock) { p = pool; }
        p.execute(() -> acceptLoop(onPartnerJoined, onError));
    }

    public void joinCoop(String code, Runnable onConnected, Consumer<String> onError) {
        if (mode != Mode.OFFLINE) { onError.accept("A co-op session is already open."); return; }

        String ip = RoomCode.toIp(code);
        if (ip == null) {
            onError.accept("That room code isn't valid. Codes look like KQXF-2M7.");
            return;
        }

        openSession(Mode.CLIENT);
        ExecutorService p;
        synchronized (lock) { p = pool; }

        p.execute(() -> {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(ip, PORT), 4000);
                s.setTcpNoDelay(true);
                s.setSoTimeout(4000);
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

                writeNow(w, Msg.of(MessageType.HELLO, PROTOCOL));
                Msg reply = Msg.decode(r.readLine());

                if (reply == null) throw new IOException("the host sent nothing");
                if (reply.type == MessageType.FULL) {
                    setupFailed(s, onError, "That room already has two players.");
                    return;
                }
                if (reply.type == MessageType.BYE) {
                    setupFailed(s, onError, "The host refused: " + reply.s(0));
                    return;
                }
                if (reply.type != MessageType.WELCOME) throw new IOException("unexpected reply");

                pendingLevel = reply.i(1);
                s.setSoTimeout(0);
                if (adopt(s, r, w)) {
                    Platform.runLater(() -> {
                        if (isSessionOpen(Mode.CLIENT)) onConnected.run();
                    });
                }
            } catch (SocketTimeoutException e) {
                setupFailed(s, onError, "No answer from the host. Same Wi-Fi? A firewall may be blocking port " + PORT + ".");
            } catch (IOException | RuntimeException e) {
                setupFailed(s, onError, "Could not connect: " + e.getMessage());
            }
        });
    }

    /** Cancel a lobby or leave a session. Safe to call any time. */
    public void close() {
        shutdownSession();
    }

    @PreDestroy
    public void shutdown() {
        shutdownSession();
    }

    // ════════════════════════════════════════════════════════════════════
    //  Called every frame by GameLoop (JavaFX thread)
    // ════════════════════════════════════════════════════════════════════

    public void update(double dt, Ship local, boolean thrusting) {
        if (mode == Mode.OFFLINE) return;

        localShip = local;
        publishLocal(local, thrusting);
        hostLevelMirror = levelService.getLevel();
        if (!connected) return;

        remote.setConnected(true);

        if (pendingLevel > 0) {
            matchLevel(pendingLevel);
            pendingLevel = -1;
        }

        Msg m;
        while ((m = inbound.poll()) != null) {
            try {
                handle(m);
            } catch (RuntimeException ignored) {
                // a malformed message must never crash the game
            }
        }

        remote.interpolate(dt);
        if (mode == Mode.HOST) syncFromHost(dt);
    }

    /** Hands the newest copy of our ship to the network thread. */
    public void publishLocal(Ship s, boolean thrusting) {
        localSnap = new ShipSnapshot(s.getX(), s.getY(), s.getVx(), s.getVy(),
                s.getAngle(), s.getHealth(), thrusting);
    }

    private void handle(Msg m) {
        switch (m.type) {
            case SHIP -> {
                remote.applySnapshot(m.d(0), m.d(1), m.d(2), m.d(3), m.d(4), (int) m.d(5), m.b(6));
                if (mode == Mode.CLIENT && !placed && localShip != null) {
                    placed = true;                                   // guest starts beside the host
                    localShip.setX(m.d(0) + CLIENT_SPAWN_OFFSET);
                    localShip.setY(m.d(1) + CLIENT_SPAWN_OFFSET);
                    localShip.setVx(m.d(2));
                    localShip.setVy(m.d(3));
                }
            }
            case FIRE    -> combatService.spawnRemotePlasma(m.d(0), m.d(1), m.d(2), m.d(3), m.d(4), m.d(5));
            case BEAM    -> combatService.spawnRemoteBeam(m.d(0));
            case LEVEL   -> matchLevel((int) m.d(0));
            case PLANETS -> applyPlanets(m);
            default      -> { }
        }
    }

    // the host's level is the shared level
    private void matchLevel(int target) {
        if (mode == Mode.HOST) return;
        for (int guard = 0; guard < 10 && levelService.getLevel() < target; guard++) levelService.levelUp();
        for (int guard = 0; guard < 10 && levelService.getLevel() > target; guard++) levelService.levelDown();
    }

    // keep both machines' planets in the same place
    private void applyPlanets(Msg m) {
        for (int i = 0; i < m.size(); i++) {
            String[] kv = m.s(i).split("=");
            if (kv.length != 2) continue;
            double angle = Double.parseDouble(kv[1]);
            for (Planet p : planets) {
                if (p.getName().equals(kv[0])) p.setOrbitalAngle(angle);
            }
        }
        // planets first, then the Moon (it rides on Earth)
        for (Planet p : planets) if (!(p instanceof Moon)) orbitalMechanics.update(p, 0);
        for (Planet p : planets) if (p instanceof Moon mo) mo.updateAroundEarth(0);
    }

    private void syncFromHost(double dt) {
        int level = levelService.getLevel();
        if (level != lastSentLevel) {
            send(Msg.of(MessageType.LEVEL, level));
            lastSentLevel = level;
        }

        planetTimer -= dt;
        if (planetTimer <= 0) {
            planetTimer = PLANET_SYNC_SECONDS;
            Object[] fields = new Object[planets.size()];
            for (int i = 0; i < fields.length; i++) {
                Planet p = planets.get(i);
                fields[i] = p.getName() + "=" + String.format(Locale.ROOT, "%.5f", p.getOrbitalAngle());
            }
            send(Msg.of(MessageType.PLANETS, fields));
        }
    }

    // CombatService tells us about every shot WE fire
    private void onLocalFire(Bullet b) {
        if (!connected) return;
        if (b.isLaser()) {
            send(Msg.of(MessageType.BEAM, b.getChargeLevel()));
        } else {
            send(Msg.of(MessageType.FIRE, b.getX(), b.getY(), b.getVx(), b.getVy(),
                    b.getAngle(), b.getLifetime()));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Network threads
    // ════════════════════════════════════════════════════════════════════

    // net-accept (host only)
    private void acceptLoop(Runnable onPartnerJoined, Consumer<String> onError) {
        ServerSocket ss;
        synchronized (lock) { ss = serverSocket; }

        while (ss != null && !closed.get()) {
            Socket s;
            try {
                s = ss.accept();                       // blocks until someone connects
            } catch (IOException e) {
                if (shutdownSession()) {
                    Platform.runLater(() -> onError.accept("The lobby closed unexpectedly."));
                }
                return;
            }

            try {
                if (connected) {                       // we already have our partner
                    refuse(s);
                    continue;
                }
                if (handshakeAsHost(s)) {
                    notice("Partner joined");
                    Platform.runLater(() -> {
                        if (isSessionOpen(Mode.HOST)) onPartnerJoined.run();
                    });
                }
            } catch (IOException | RuntimeException e) {
                closeQuietly(s);
            }
        }
    }

    private boolean handshakeAsHost(Socket s) throws IOException {
        s.setTcpNoDelay(true);
        s.setSoTimeout(3000);
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

        Msg hello = Msg.decode(r.readLine());
        if (hello == null || hello.type != MessageType.HELLO) {
            closeQuietly(s);
            return false;
        }
        if (hello.i(0) != PROTOCOL) {
            writeNow(w, Msg.of(MessageType.BYE, "Game versions differ"));
            closeQuietly(s);
            return false;
        }

        writeNow(w, Msg.of(MessageType.WELCOME, PROTOCOL, hostLevelMirror));
        s.setSoTimeout(0);
        return adopt(s, r, w);
    }

    private void refuse(Socket s) {
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            writeNow(w, Msg.of(MessageType.FULL));
        } catch (IOException ignored) {
        } finally {
            closeQuietly(s);
        }
    }

    // start the three connection threads once the handshake is done
    private boolean adopt(Socket s, BufferedReader r, BufferedWriter w) {
        synchronized (lock) {
            if (closed.get()) { closeQuietly(s); return false; }
            socket = s;
            in = r;
            out = w;
            lastRxNanos = System.nanoTime();
            connected = true;
            pool.execute(this::readLoop);
            pool.execute(this::writeLoop);
            ticker.scheduleAtFixedRate(this::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
            return true;
        }
    }

    // net-reader
    private void readLoop() {
        BufferedReader r;
        synchronized (lock) { r = in; }
        if (r == null) return;

        try {
            String line;
            while ((line = r.readLine()) != null) {
                lastRxNanos = System.nanoTime();
                Msg m = Msg.decode(line);
                if (m == null || m.type == MessageType.PING) continue;
                if (m.type == MessageType.BYE) { fail("Your partner left the game."); return; }
                if (inbound.size() < 2000) inbound.add(m);
            }
            fail("Your partner closed the game.");
        } catch (IOException e) {
            fail("Connection lost.");
        }
    }

    // net-writer
    private void writeLoop() {
        BufferedWriter w;
        synchronized (lock) { w = out; }
        if (w == null) return;

        List<String> batch = new ArrayList<>();
        try {
            while (!closed.get()) {
                batch.add(outbound.take());            // waits until there is something to send
                outbound.drainTo(batch);               // grab everything else too, flush once
                synchronized (writeLock) {
                    for (String line : batch) {
                        w.write(line);
                        w.write('\n');
                    }
                    w.flush();
                }
                batch.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            fail("Connection lost.");
        }
    }

    // net-tick: 20 times per second
    private void tick() {
        try {
            if (!connected) return;

            ShipSnapshot s = localSnap;
            if (s != null && outbound.size() < 40) {   // don't pile up state on a slow link
                send(Msg.of(MessageType.SHIP, s.x(), s.y(), s.vx(), s.vy(),
                        s.angle(), s.health(), s.thrusting()));
            }
            if (++tickCount % 20 == 0) send(Msg.of(MessageType.PING));

            if (System.nanoTime() - lastRxNanos > TIMEOUT_NANOS) {
                fail("Your partner stopped responding.");
            }
        } catch (RuntimeException ignored) {
            // an exception would silently cancel a scheduled task, so swallow it
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Session plumbing
    // ════════════════════════════════════════════════════════════════════

    private void openSession(Mode m) {
        synchronized (lock) {
            closed.set(false);
            mode = m;
            connected = false;
            pendingLevel = -1;
            outbound.clear();
            inbound.clear();
            tickCount = 0;
            pool = Executors.newCachedThreadPool(daemonThreads("net"));
            ticker = Executors.newSingleThreadScheduledExecutor(daemonThreads("net-tick"));
        }
        placed = false;
        lastSentLevel = -1;
        planetTimer = 0;
        hostLevelMirror = levelService.getLevel();
    }

    /** Returns true if THIS call did the closing (false if it was already closed). */
    private boolean shutdownSession() {
        ExecutorService p;
        ScheduledExecutorService t;
        ServerSocket ss;
        Socket s;
        BufferedWriter w;
        boolean wasConnected;

        synchronized (lock) {
            if (closed.getAndSet(true)) return false;
            wasConnected = connected;
            connected = false;
            mode = Mode.OFFLINE;
            p = pool;   t = ticker;   ss = serverSocket;   s = socket;   w = out;
            pool = null; ticker = null; serverSocket = null; socket = null; in = null; out = null;
        }

        if (wasConnected && w != null) {
            try { writeNow(w, Msg.of(MessageType.BYE, "left")); } catch (IOException ignored) { }
        }
        closeQuietly(ss);        // wakes up the accept thread
        closeQuietly(s);         // wakes up the reader thread
        if (t != null) t.shutdownNow();
        if (p != null) p.shutdownNow();   // interrupts the writer thread
        outbound.clear();

        try {
            Platform.runLater(() -> {
                remote.setConnected(false);
                combatService.clearRemoteBullets();
            });
        } catch (IllegalStateException ignored) {
            // JavaFX is already shutting down
        }
        return true;
    }

    // something went wrong on a network thread
    private void fail(String reason) {
        if (shutdownSession()) notice(reason);
    }

    private void setupFailed(Socket s, Consumer<String> onError, String message) {
        closeQuietly(s);
        if (shutdownSession()) {
            Platform.runLater(() -> onError.accept(message));
        }
    }

    private boolean isSessionOpen(Mode expectedMode) {
        return !closed.get() && mode == expectedMode;
    }

    private void notice(String text) {
        Platform.runLater(() -> {
            Consumer<String> l = noticeListener;
            if (l != null) l.accept(text);
        });
    }

    private void send(Msg m) {
        outbound.offer(m.encode());
    }

    private void writeNow(BufferedWriter w, Msg m) throws IOException {
        synchronized (writeLock) {
            w.write(m.encode());
            w.write('\n');
            w.flush();
        }
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);                     // never keeps the game open after the window closes
            return t;
        };
    }

    private static void closeQuietly(Closeable c) {
        try { if (c != null) c.close(); } catch (IOException ignored) { }
    }

    // ── getters ──────────────────────────────────────────────────────────

    public boolean isActive()    { return mode != Mode.OFFLINE; }
    public boolean isHost()      { return mode == Mode.HOST; }
    public boolean isClient()    { return mode == Mode.CLIENT; }
    public boolean isConnected() { return connected; }
    public String  getRoomCode() { return roomCode; }
    public void    setNoticeListener(Consumer<String> listener) { this.noticeListener = listener; }
}