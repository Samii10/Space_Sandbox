package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.Asteroid;
import project.aoop.space_sandbox.entity.Bullet;
import project.aoop.space_sandbox.entity.CelestialBody;
import project.aoop.space_sandbox.entity.Debris;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.entity.Star;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class AsteroidService {

    // ── Belt layout (world units, centred on the Sun) ────────────────────
    public static final double BELT_INNER = 1000.0;   // Mars orbit is 750
    public static final double BELT_OUTER = 1700.0;   // Jupiter orbit is 2000
    public static final int    MAX_HULL   = 100;

    private static final double BELT_EDGE_FADE = 90.0;   // belt thins out over this distance at both edges
    private static final double BELT_DENSITY   = 12.0;   // asteroids per 1000 square world units
    private static final double BELT_DRIFT     = 0.0;    // keep the belt identical on both screens
    private static final double CELL           = 40.0;   // belt is generated in 40x40 chunks
    private static final int    MAX_PER_CELL   = 60;
    private static final long   SEED           = 90210L;

    // ── Streaming around the ship ────────────────────────────────────────
    private static final double MIN_ACTIVE_RADIUS = 110.0;
    private static final double MAX_ACTIVE_RADIUS = 320.0;
    private static final double UNLOAD_INTERVAL   = 0.5;
    private static final int    MAX_ACTIVE        = 2500;
    private static final int    MAX_DEBRIS        = 500;

    // ── Incoming asteroids ───────────────────────────────────────────────
    private static final double FIRST_WAVE_DELAY = 25.0;  // grace period at game start
    private static final double WARNING_SECONDS  = 4.0;   // warning time before one can reach you
    private static final double THREAT_HORIZON   = 14.0;  // only warn about impacts within this many seconds
    private static final double THREAT_MARGIN    = 6.0;   // counts as "on course" if it passes this close

    // ── Damage ───────────────────────────────────────────────────────────
    private static final double BULLET_DAMAGE    = 1.0;
    private static final double BULLET_RADIUS    = 0.4;
    private static final double LASER_DPS_BASE   = 6.0;   // damage per second to everything in the beam
    private static final double LASER_DPS_CHARGE = 24.0;  // extra at full charge

    private static final double HIT_INVULN       = 0.45;  // seconds of protection after a hit
    private static final double HULL_REGEN_DELAY = 8.0;   // seconds without a hit before repairs start
    private static final double HULL_REGEN_RATE  = 2.0;   // hull points per second (0 = no regen)

    /** An incoming asteroid that is on a collision course with the ship. */
    public static class Threat {
        public final Asteroid asteroid;
        public final double   eta;        // seconds until closest approach
        public final double   distance;   // distance to the ship right now

        Threat(Asteroid asteroid, double eta, double distance) {
            this.asteroid = asteroid;
            this.eta = eta;
            this.distance = distance;
        }
    }

    private final GameCamera    camera;
    private final LevelService  levelService;
    private final CombatService combatService;

    private final List<Asteroid>      asteroids   = new ArrayList<>();
    private final List<Debris>        debris      = new ArrayList<>();
    private final List<Threat>        threats     = new ArrayList<>();
    private final Map<Long, int[]>    loadedCells = new HashMap<>();
    private final Set<Long>           destroyedIds = new HashSet<>();
    private final List<CelestialBody> solids      = new ArrayList<>();
    private final Random              rng         = new Random();

    private double  spawnTimer    = FIRST_WAVE_DELAY;
    private double  unloadTimer   = 0;
    private long    incomingCount = 0;
    private int     destroyedCount = 0;
    private boolean inBelt        = false;

    private double invuln      = 0;
    private double hitFlash    = 0;
    private double sinceHit    = 999;
    private double regenAccum  = 0;

    public AsteroidService(GameCamera camera, LevelService levelService,
                           CombatService combatService, Star star, List<Planet> planets) {
        this.camera = camera;
        this.levelService = levelService;
        this.combatService = combatService;
        solids.add(star);
        solids.addAll(planets);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Frame update (call AFTER the ship and CombatService have updated)
    // ════════════════════════════════════════════════════════════════════

    public void update(double dt, Ship ship) {
        inBelt = isBeltRadius(Math.hypot(ship.getX(), ship.getY()), 40.0);

        streamBelt(dt, ship);
        updateIncoming(dt, ship);

        for (Asteroid a : asteroids) a.update(dt);

        hitByWeapons(dt);
        hitSolids();
        hitShip(dt, ship);
        reapDead();
        cullIncoming(ship);
        updateDebris(dt);
        updateThreats(ship);
        updateHull(dt, ship);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Belt: generated deterministically in chunks around the ship
    // ════════════════════════════════════════════════════════════════════

    private double activeRadius() {
        double halfDiag = Math.hypot(camera.getScreenW(), camera.getScreenH()) / 2.0 / camera.getZoom();
        return Math.max(MIN_ACTIVE_RADIUS, Math.min(MAX_ACTIVE_RADIUS, halfDiag + 60.0));
    }

    private boolean isBeltRadius(double d, double margin) {
        return d >= BELT_INNER - margin && d <= BELT_OUTER + margin;
    }

    // 0..1: full density in the middle of the belt, thinning out at both edges
    private double beltDensityAt(double d) {
        if (d < BELT_INNER || d > BELT_OUTER) return 0;
        double t = Math.min((d - BELT_INNER) / BELT_EDGE_FADE, (BELT_OUTER - d) / BELT_EDGE_FADE);
        return Math.max(0, Math.min(1, t));
    }

    private void streamBelt(double dt, Ship ship) {
        double R  = activeRadius();
        double px = ship.getX(), py = ship.getY();
        double ds = Math.hypot(px, py);

        // only bother loading chunks when the ship is near the belt
        if (ds + R >= BELT_INNER && ds - R <= BELT_OUTER) {
            int minX = (int) Math.floor((px - R) / CELL), maxX = (int) Math.floor((px + R) / CELL);
            int minY = (int) Math.floor((py - R) / CELL), maxY = (int) Math.floor((py + R) / CELL);

            for (int cx = minX; cx <= maxX; cx++) {
                for (int cy = minY; cy <= maxY; cy++) {
                    double mx = (cx + 0.5) * CELL - px;
                    double my = (cy + 0.5) * CELL - py;
                    if (mx * mx + my * my > R * R) continue;

                    long key = cellKey(cx, cy);
                    if (loadedCells.containsKey(key)) continue;
                    if (asteroids.size() >= MAX_ACTIVE) return;
                    loadCell(cx, cy, key);
                }
            }
        }

        unloadTimer -= dt;
        if (unloadTimer <= 0) {
            unloadTimer = UNLOAD_INTERVAL;
            unloadFarCells(px, py, R);
        }
    }

    private long cellKey(int cx, int cy) {
        return cx * 1_000_003L + cy;
    }

    private void loadCell(int cx, int cy, long key) {
        loadedCells.put(key, new int[]{cx, cy});

        double midX = (cx + 0.5) * CELL, midY = (cy + 0.5) * CELL;
        double d    = Math.hypot(midX, midY);
        double half = CELL * 0.7072;
        if (d + half < BELT_INNER || d - half > BELT_OUTER) return;   // chunk is nowhere near the belt

        // Same chunk -> same asteroids, every time
        Random r = new Random(SEED ^ (cx * 0x9E3779B97F4A7C15L) ^ (cy * 0xC2B2AE3D27D4EB4FL));

        double expected = BELT_DENSITY * CELL * CELL / 1000.0;
        int n = (int) expected + (r.nextDouble() < expected - (int) expected ? 1 : 0);
        n = Math.min(n, MAX_PER_CELL);

        for (int i = 0; i < n; i++) {
            // draw every random number first, so asteroid #i is always the same one
            double px       = (cx + r.nextDouble()) * CELL;
            double py       = (cy + r.nextDouble()) * CELL;
            double accept   = r.nextDouble();
            double sizeRoll = r.nextDouble();
            double dir      = r.nextDouble() * Math.PI * 2;
            double drift    = r.nextDouble() * BELT_DRIFT;
            double spin     = (r.nextDouble() - 0.5) * 1.6;
            double angle0   = r.nextDouble() * Math.PI * 2;
            double tint     = r.nextDouble();
            long   shapeSeed = r.nextLong();

            if (accept > beltDensityAt(Math.hypot(px, py))) continue;

            long id = cx * (1L << 40) + cy * (1L << 16) + i;
            if (destroyedIds.contains(id)) continue;
            if (asteroids.size() >= MAX_ACTIVE) return;

            double radius = 1.2 + 4.6 * Math.pow(sizeRoll, 2.4);   // many small, a few big
            asteroids.add(new Asteroid(id, key, Asteroid.Kind.BELT, px, py,
                    Math.cos(dir) * drift, Math.sin(dir) * drift,
                    radius, angle0, spin, tint, shapeSeed));
        }
    }

    private void unloadFarCells(double px, double py, double R) {
        double limit = R + 2 * CELL;
        boolean removed = false;

        Iterator<Map.Entry<Long, int[]>> it = loadedCells.entrySet().iterator();
        while (it.hasNext()) {
            int[] c = it.next().getValue();
            double mx = (c[0] + 0.5) * CELL - px;
            double my = (c[1] + 0.5) * CELL - py;
            if (mx * mx + my * my > limit * limit) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            asteroids.removeIf(a -> a.getKind() == Asteroid.Kind.BELT
                    && !loadedCells.containsKey(a.getCellKey()));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Incoming asteroids: aimed once, then dead straight (no homing)
    // ════════════════════════════════════════════════════════════════════

    private void updateIncoming(double dt, Ship ship) {
        if (inBelt) {                        // inside the belt, the belt is the hazard
            spawnTimer = Math.max(spawnTimer, 6.0);
            return;
        }
        spawnTimer -= dt;
        if (spawnTimer > 0) return;

        spawnWave(ship);
        spawnTimer = nextInterval();
    }

    private double nextInterval() {
        double base = Math.max(8.0, 26.0 - 2.2 * (levelService.getLevel() - 1));
        return base * (0.6 + rng.nextDouble() * 0.8);
    }

    private double incomingSpeed() {
        return Math.max(9.0, Math.min(55.0, levelService.getSpeedLimit() * 0.65));
    }

    private void spawnWave(Ship ship) {
        int count = 1 + rng.nextInt(2);                                          // 1 or 2
        if (rng.nextDouble() < 0.22) count++;                                    // sometimes one more
        if (levelService.getLevel() >= 5 && rng.nextDouble() < 0.30) count++;    // bigger waves later
        count = Math.min(count, 4);

        double baseSpeed = incomingSpeed();
        double halfDiag  = Math.hypot(camera.getScreenW(), camera.getScreenH()) / 2.0 / camera.getZoom();
        double spawnDist = Math.max(60.0, Math.min(700.0, halfDiag + baseSpeed * WARNING_SECONDS + 10.0));
        double baseAngle = rng.nextDouble() * Math.PI * 2;      // the wave comes from one side

        for (int i = 0; i < count; i++) {
            double a     = baseAngle + (rng.nextDouble() - 0.5) * 0.9;
            double dist  = spawnDist + i * 9.0 + rng.nextDouble() * 14.0;
            double x     = ship.getX() + Math.cos(a) * dist;
            double y     = ship.getY() + Math.sin(a) * dist;
            double speed = baseSpeed * (0.85 + rng.nextDouble() * 0.35);

            // Aim where the ship WILL be if it keeps its course. After this the asteroid
            // never steers, so changing course (or shooting it) saves you.
            double t  = dist / speed;
            double tx = ship.getX(), ty = ship.getY();
            for (int k = 0; k < 2; k++) {
                tx = ship.getX() + ship.getVx() * t;
                ty = ship.getY() + ship.getVy() * t;
                t  = Math.min(10.0, Math.hypot(tx - x, ty - y) / speed);
            }
            double aim = Math.atan2(ty - y, tx - x) + (rng.nextDouble() - 0.5) * 0.10;

            double radius = 1.8 + 3.0 * Math.pow(rng.nextDouble(), 1.6);
            asteroids.add(new Asteroid(-(++incomingCount), 0L, Asteroid.Kind.INCOMING,
                    x, y, Math.cos(aim) * speed, Math.sin(aim) * speed,
                    radius, rng.nextDouble() * Math.PI * 2, (rng.nextDouble() - 0.5) * 2.4,
                    rng.nextDouble(), rng.nextLong()));
        }
        System.out.println("☄ " + count + " asteroid" + (count > 1 ? "s" : "") + " incoming");
    }

    private void cullIncoming(Ship ship) {
        asteroids.removeIf(a -> a.getKind() == Asteroid.Kind.INCOMING
                && (a.getAge() > 45.0
                || Math.hypot(a.getX() - ship.getX(), a.getY() - ship.getY()) > 800.0));
    }

    // ════════════════════════════════════════════════════════════════════
    //  Collisions
    // ════════════════════════════════════════════════════════════════════

    // Bullets (swept test, so fast shots can't skip over a rock) and the laser beam
    private void hitByWeapons(double dt) {
        List<Bullet> bullets = combatService.getBullets();

        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            if (b.isLaser()) continue;

            double x1 = b.getX(), y1 = b.getY();
            double x0 = x1 - b.getVx() * dt, y0 = y1 - b.getVy() * dt;
            double reachBase = Math.hypot(b.getVx(), b.getVy()) * dt + 1.0;

            for (Asteroid a : asteroids) {
                if (a.isDead()) continue;
                double reach = reachBase + a.getRadius();
                double ddx = a.getX() - x1, ddy = a.getY() - y1;
                if (ddx * ddx + ddy * ddy > reach * reach) continue;

                double rr = a.getRadius() + BULLET_RADIUS;
                if (distSqToSegment(a.getX(), a.getY(), x0, y0, x1, y1) <= rr * rr) {
                    a.damage(BULLET_DAMAGE);
                    for (int k = 0; k < 3 && debris.size() < MAX_DEBRIS; k++) {
                        double ang = rng.nextDouble() * Math.PI * 2;
                        double spd = 3.0 + rng.nextDouble() * 6.0;
                        debris.add(Debris.spark(x1, y1, Math.cos(ang) * spd, Math.sin(ang) * spd));
                    }
                    it.remove();
                    break;
                }
            }
        }

        // The laser pierces: everything inside the beam takes damage
        for (Bullet b : bullets) {
            if (!b.isLaser()) continue;
            double env = b.getEnvelope();
            if (env <= 0.05) continue;

            double x0 = b.getX(), y0 = b.getY();
            double x1 = x0 + Math.cos(b.getAngle()) * b.getBeamLength();
            double y1 = y0 + Math.sin(b.getAngle()) * b.getBeamLength();
            double hw  = b.getBeamHalfWidth();
            double dps = LASER_DPS_BASE + LASER_DPS_CHARGE * b.getChargeLevel();

            for (Asteroid a : asteroids) {
                if (a.isDead()) continue;
                double rr = a.getRadius() + hw;
                if (distSqToSegment(a.getX(), a.getY(), x0, y0, x1, y1) <= rr * rr) {
                    a.damage(dps * env * dt);
                }
            }
        }
    }

    // Planets and the Sun stop asteroids too
    private void hitSolids() {
        for (Asteroid a : asteroids) {
            if (a.isDead()) continue;
            for (CelestialBody s : solids) {
                double dx = a.getX() - s.getX(), dy = a.getY() - s.getY();
                double rr = s.getRadius() + a.getRadius() * 0.6;
                if (dx * dx + dy * dy <= rr * rr) {
                    a.destroyNoCredit();
                    break;
                }
            }
        }
    }

    private void hitShip(double dt, Ship ship) {
        invuln   = Math.max(0, invuln - dt);
        hitFlash = Math.max(0, hitFlash - dt * 2.5);

        double shipR = ShipGeometry.COLLISION_RADIUS;
        for (Asteroid a : asteroids) {
            if (a.isDead()) continue;
            double dx = a.getX() - ship.getX(), dy = a.getY() - ship.getY();
            double rr = a.getRadius() + shipR;
            if (dx * dx + dy * dy > rr * rr) continue;

            if (invuln <= 0) {
                double rel = Math.hypot(a.getVx() - ship.getVx(), a.getVy() - ship.getVy());
                int dmg = (int) Math.round(Math.min(30.0, 3.0 + a.getRadius() * 2.2 + rel * 0.08));
                ship.setHealth(Math.max(0, ship.getHealth() - dmg));
                invuln   = HIT_INVULN;
                hitFlash = 1.0;
                sinceHit = 0;
                System.out.println("💥 Asteroid hit: -" + dmg + " hull (" + ship.getHealth() + " left)");
            }
            a.destroy();   // the rock shatters either way
        }
    }

    private void reapDead() {
        Iterator<Asteroid> it = asteroids.iterator();
        while (it.hasNext()) {
            Asteroid a = it.next();
            if (!a.isDead()) continue;

            explode(a);
            if (a.getKind() == Asteroid.Kind.BELT) destroyedIds.add(a.getId());   // stays gone
            if (a.isCredited()) destroyedCount++;
            it.remove();
        }
    }

    private void explode(Asteroid a) {
        debris.add(Debris.flash(a.getX(), a.getY(), a.getRadius() * 1.8));
        int chips = 6 + (int) (a.getRadius() * 2.5);
        for (int i = 0; i < chips && debris.size() < MAX_DEBRIS; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double spd = 2.0 + rng.nextDouble() * 7.0;
            debris.add(Debris.chip(a.getX(), a.getY(),
                    a.getVx() + Math.cos(ang) * spd, a.getVy() + Math.sin(ang) * spd,
                    0.25 + rng.nextDouble() * 0.55, 0.5 + rng.nextDouble() * 0.7, a.getTint()));
        }
    }

    private void updateDebris(double dt) {
        Iterator<Debris> it = debris.iterator();
        while (it.hasNext()) {
            Debris d = it.next();
            d.update(dt);
            if (d.isDead()) it.remove();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Threat prediction (feeds the danger indicator)
    // ════════════════════════════════════════════════════════════════════

    private void updateThreats(Ship ship) {
        threats.clear();
        double shipR = ShipGeometry.COLLISION_RADIUS;

        for (Asteroid a : asteroids) {
            if (a.getKind() != Asteroid.Kind.INCOMING) continue;

            double rx = a.getX() - ship.getX(), ry = a.getY() - ship.getY();
            double vx = a.getVx() - ship.getVx(), vy = a.getVy() - ship.getVy();
            double v2 = vx * vx + vy * vy;
            if (v2 < 1e-6) continue;

            double t = -(rx * vx + ry * vy) / v2;          // time of closest approach
            if (t <= 0 || t > THREAT_HORIZON) continue;    // already passing, or too far off

            double mx = rx + vx * t, my = ry + vy * t;
            double miss = Math.sqrt(mx * mx + my * my);
            if (miss > a.getRadius() + shipR + THREAT_MARGIN) continue;   // will miss: no alarm

            threats.add(new Threat(a, t, Math.hypot(rx, ry)));
        }
        threats.sort((p, q) -> Double.compare(p.eta, q.eta));
    }

    // Slow hull repair after a quiet spell
    private void updateHull(double dt, Ship ship) {
        sinceHit += dt;
        if (HULL_REGEN_RATE <= 0) return;
        if (ship.getHealth() <= 0 || ship.getHealth() >= MAX_HULL) return;
        if (sinceHit < HULL_REGEN_DELAY) return;

        regenAccum += HULL_REGEN_RATE * dt;
        int whole = (int) regenAccum;
        if (whole > 0) {
            ship.setHealth(Math.min(MAX_HULL, ship.getHealth() + whole));
            regenAccum -= whole;
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static double distSqToSegment(double px, double py,
                                          double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double len2 = dx * dx + dy * dy;
        double t = len2 < 1e-12 ? 0 : ((px - x0) * dx + (py - y0) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = x0 + dx * t - px, cy = y0 + dy * t - py;
        return cx * cx + cy * cy;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public List<Asteroid> getAsteroids()    { return asteroids; }
    public List<Debris>   getDebris()       { return debris; }
    public List<Threat>   getThreats()      { return threats; }
    public boolean        isInBelt()        { return inBelt; }
    public int            getDestroyedCount() { return destroyedCount; }
    public double         getHitFlash()     { return hitFlash; }
}