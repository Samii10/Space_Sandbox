package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.entity.Bullet;
import project.aoop.space_sandbox.entity.CelestialBody;
import project.aoop.space_sandbox.entity.EnemyKind;
import project.aoop.space_sandbox.entity.EnemyShip;
import project.aoop.space_sandbox.entity.EnemyShip.LaserState;
import project.aoop.space_sandbox.entity.Impact;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.entity.Star;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Spawns the enemy sector of the CURRENT LEVEL (see EnemySector), steers the ships, fires
 * their plasma and lasers, runs the boss attacks and resolves every hit.
 *
 * Enemy plasma lives in CombatService's bullet list (team = ENEMY) so planets block it and
 * BulletRenderer draws it. Enemy lasers are owned by the ship (see EnemyShip / EnemyRenderer).
 *
 * Call update(dt, ship) AFTER combatService.update(...) each frame.
 */
@Service
public class EnemyService {

    // ── movement ──
    private static final double ACCEL          = 14.0;
    private static final double TURN_SPEED     = 1.5;    // rad/s (bosses turn slower)
    private static final double STRAFE_WEIGHT  = 0.6;
    private static final double PATROL_RATE    = 0.06;   // rad/s of the idle patrol around the planet
    private static final double SURROUND_OMEGA = 0.25;   // how fast the flanking slots rotate around you

    // ── weapons ──
    private static final double AIM_TOLERANCE  = 0.12;   // rad
    private static final double BULLET_SPEED   = 26.0;
    private static final double BULLET_RADIUS  = 0.15;
    private static final double ALERT_TIME     = 10.0;   // seconds a squad stays alerted

    // ── aiming: accurate once, then they must re-aim ──
    private static final double LASER_LOCK_TIME = 1.0;   // beam direction freezes this long before it fires
    private static final double BEAM_REACT      = 0.35;  // after firing, beam stays put this long before it adjusts

    // ── tether: ships never leave their planet ──
    private static final double TETHER_GRUNT            = 120.0;  // max distance beyond the planet's gravity radius
    private static final double TETHER_BOSS             = 160.0;
    private static final double TETHER_PERSISTENT_EXTRA = 60.0;   // Neptune / Earth squads roam a bit further
    private static final double ENGAGE_MARGIN           = 60.0;   // they start hunting this far outside their zone

    private static final boolean HEAL_ON_NEW_SECTOR = true;       // full health when a new sector begins

    // ── damage from the player ──
    private static final double PLAYER_BULLET_DAMAGE = 10.0;
    private static final double LASER_DPS_BASE       = 20.0;
    private static final double LASER_DPS_CHARGE     = 40.0;

    private final List<EnemyShip>     enemies = new ArrayList<>();
    private final List<EnemyShip>     pendingSpawns = new ArrayList<>();   // reinforcements, added after the loop
    private final List<CelestialBody> solids  = new ArrayList<>();
    private final List<Planet>        planets;
    private final CombatService       combat;
    private final LevelService        levelService;
    private final Random              rng = new Random();

    private EnemySector sector;               // null on safe levels
    private Planet      sectorPlanet;
    private int         spawnedLevel = -1;
    private double      clock        = 0;
    private double      alertTimer   = 0;
    private double      playerDmgCarry = 0;
    private boolean     playerKilled = false;
    private boolean     finalBossDefeated = false;
    private boolean     clearedReported   = false;

    public EnemyService(CombatService combat, LevelService levelService,
                        Star star, List<Planet> planets) {
        this.combat       = combat;
        this.levelService = levelService;
        this.planets      = planets;
        solids.add(star);
        solids.addAll(planets);
    }

    // ── Frame update ─────────────────────────────────────────────────────

    public void update(double dt, Ship ship) {
        clock += dt;
        syncSector(ship);
        if (alertTimer > 0) alertTimer -= dt;

        for (EnemyShip e : enemies) {
            e.tick(dt);
            think(e, ship, dt);
            e.move(dt);
            pushOutOfBodies(e);
            tether(e);
        }

        enemies.addAll(pendingSpawns);
        pendingSpawns.clear();

        resolveBulletHits(ship, dt);
        removeDead();
    }

    /** Call when the game restarts (GameSession reset / leaveToMenu). */
    public void reset() {
        enemies.clear();
        combat.getBullets().removeIf(Bullet::isEnemy);
        sector = null;
        spawnedLevel = -1;
        alertTimer = 0;
        playerDmgCarry = 0;
        playerKilled = false;
        finalBossDefeated = false;
        clearedReported = false;
    }

    // ── Sector spawning (follows the level) ──────────────────────────────

    private void syncSector(Ship ship) {
        int lvl = levelService.getLevel();
        if (lvl == spawnedLevel) return;
        spawnedLevel = lvl;
        if (HEAL_ON_NEW_SECTOR && ship.getHealth() > 0) ship.setHealth(100);

        enemies.clear();
        combat.getBullets().removeIf(Bullet::isEnemy);
        alertTimer = 0;
        clearedReported = false;

        sector = EnemySector.forLevel(lvl);
        sectorPlanet = null;
        if (sector != null) {
            for (Planet p : planets) {
                if (sector.planet().equalsIgnoreCase(p.getName())) { sectorPlanet = p; break; }
            }
            if (sectorPlanet == null) { sector = null; return; }
            spawnSector();
        }
    }

    private void spawnSector() {
        int total = sector.total();
        int idx = 0;

        for (int i = 0; i < sector.grunts(); i++) {
            EnemyShip e = newShip(EnemyKind.GRUNT, idx++, total);
            e.setLaserCapable(sector.laserGrunts() < 0 || i < sector.laserGrunts());
            enemies.add(e);
        }
        if (sector.boss() != null) {
            EnemyShip b = newShip(sector.boss(), idx, total);
            b.setLaserCapable(sector.laserDps() > 0);
            enemies.add(b);
        }
    }

    private EnemyShip newShip(EnemyKind kind, int index, int total) {
        Planet p = sectorPlanet;
        double ang = rng.nextDouble() * Math.PI * 2;
        double r = kind.isBoss() ? p.getGravityRadius() + 70
                : p.getGravityRadius() + 25 + rng.nextDouble() * 45;
        EnemyShip e = new EnemyShip(kind,
                p.getX() + Math.cos(ang) * r, p.getY() + Math.sin(ang) * r,
                rng.nextDouble() * Math.PI * 2, rng.nextBoolean() ? 1 : -1);
        double rate = (rng.nextBoolean() ? 1 : -1) * PATROL_RATE * (kind.isBoss() ? 0.5 : 1.0);
        e.setHome(ang, r, rate);
        e.setSlot(index * Math.PI * 2 / Math.max(1, total));
        e.setRangeBias(kind.isBoss() ? 0 : -4 + rng.nextDouble() * 10);
        e.setLaserCooldown(2.0 + rng.nextDouble() * 8.0);   // stagger the first beams
        if (!kind.isBoss()) e.scaleHealth(1.0 + 0.15 * (spawnedLevel - 1));
        return e;
    }

    private double[] patrolPoint(EnemyShip e, double dt) {
        if (sectorPlanet == null) return new double[]{e.getX(), e.getY()};
        e.advanceHome(dt);
        return new double[]{
                sectorPlanet.getX() + Math.cos(e.getHomeAngle()) * e.getHomeRadius(),
                sectorPlanet.getY() + Math.sin(e.getHomeAngle()) * e.getHomeRadius()
        };
    }

    // ── AI ───────────────────────────────────────────────────────────────

    private void think(EnemyShip e, Ship ship, double dt) {
        EnemyKind k = e.getKind();
        boolean playerAlive = ship.getHealth() > 0;
        double dx = ship.getX() - e.getX();
        double dy = ship.getY() - e.getY();
        double dist = Math.max(1e-6, Math.hypot(dx, dy));

        double[] home = patrolPoint(e, dt);
        double homeDist = Math.hypot(e.getX() - home[0], e.getY() - home[1]);

        // who is hunting? Only while the player is inside THIS planet's zone
        double tether = tetherRadius(e);
        double pdx = ship.getX() - sectorPlanet.getX();
        double pdy = ship.getY() - sectorPlanet.getY();
        boolean playerInZone = Math.hypot(pdx, pdy) <= tether + ENGAGE_MARGIN;
        boolean persistent = sector.persistent();
        double aggro = persistent ? 700 : (k.isBoss() ? 400 : 170);
        boolean sees = playerAlive && playerInZone && dist <= aggro;
        if (sees && sector.shareAlert()) alertTimer = ALERT_TIME;
        boolean hunting = playerAlive && playerInZone && (sees || (alertTimer > 0 && dist <= 800));

        double baseMax  = k.maxSpeed * sector.speedMult()
                * (k == EnemyKind.BOSS_FINAL && e.getPhase() >= 3 ? 1.25 : 1.0);
        double maxSpeed = baseMax;
        double prefMin  = k.prefMin + e.getRangeBias();
        double prefMax  = k.prefMax + e.getRangeBias();
        LaserState ls   = e.getLaserState();

        double mvx, mvy, aim;
        double acc = sector.accuracy();

        if (hunting) {
            double ux = dx / dist, uy = dy / dist;

            // persistent squads catch up with a player who tries to run
            if (persistent && dist > prefMax * 1.5) {
                maxSpeed = Math.max(baseMax, Math.min(60.0, ship.getSpeed() * 1.1));
            }

            if (sector.surround() && !k.isBoss()) {
                // flank / gang up: every ship heads for its own slot on a ring around the player
                double slot = e.getSlot() + clock * SURROUND_OMEGA;
                double px = ship.getX() + Math.cos(slot) * prefMax - e.getX();
                double py = ship.getY() + Math.sin(slot) * prefMax - e.getY();
                double pd = Math.max(1e-6, Math.hypot(px, py));
                double sp = Math.min(maxSpeed, pd * 1.2);
                mvx = px / pd * sp;
                mvy = py / pd * sp;
            } else {
                double radial = dist > prefMax ? 1 : (dist < prefMin ? -1 : 0);
                double tx = -uy * e.getStrafeDir(), ty = ux * e.getStrafeDir();
                mvx = ux * radial + tx * STRAFE_WEIGHT;
                mvy = uy * radial + ty * STRAFE_WEIGHT;
                double len = Math.hypot(mvx, mvy);
                if (len > 1) { mvx /= len; mvy /= len; }
                mvx *= maxSpeed;
                mvy *= maxSpeed;
            }

            // ships hold still-ish while charging / firing a laser
            double f = ls == LaserState.FIRING ? 0.3 : (ls == LaserState.CHARGING ? 0.5 : 1.0);
            mvx *= f;
            mvy *= f;
            // loosely match the player's drift so it keeps up
            mvx += ship.getVx() * 0.8;
            mvy += ship.getVy() * 0.8;

            // ── aiming ──
            // Plasma: the enemy only takes a fresh firing solution every `reaction` seconds
            // (continuously, until it has fired, for the opening shot). In between it keeps aiming
            // at the OLD intercept point, so if you change direction its next shots miss
            // until it has re-aimed.
            // Laser: tracks you while charging, then LOCKS its direction for the last second.
            // Dodge after the lock and the beam fires at the spot you left.
            acc = k.isBoss() ? 0.85
                    : (sector.accurateOpener() && e.isOpenerReady() ? 1.0 : sector.accuracy());

            if (ls == LaserState.CHARGING) {
                double left = e.getChargeTime() - e.getLaserTimer();
                if (left > LASER_LOCK_TIME) {
                    aim = Math.atan2(dy, dx);                       // still tracking you
                } else {
                    if (!e.isLocked()) e.lockAim(Math.atan2(dy, dx)); // exact aim, taken ONCE
                    aim = e.getLockAngle();
                }
            } else if (ls == LaserState.FIRING) {
                boolean reacting = EnemyShip.BEAM_DURATION - e.getLaserTimer() >= BEAM_REACT;
                aim = reacting ? Math.atan2(dy, dx) : e.getLockAngle();
            } else {
                boolean opener = sector.accurateOpener() && e.isOpenerReady();
                if (opener || e.getAimRefresh() <= 0) {
                    double flight = dist / BULLET_SPEED;
                    e.setAimPoint(ship.getX() + (ship.getVx() - e.getVx()) * flight * acc,
                            ship.getY() + (ship.getVy() - e.getVy()) * flight * acc);
                    e.setAimRefresh(reactionFor(e));
                }
                aim = Math.atan2(e.getAimY() - e.getY(), e.getAimX() - e.getX());
            }
        } else {
            e.setOpenerReady(true);
            e.setAimRefresh(0);
            double hx = home[0] - e.getX(), hy = home[1] - e.getY();
            double speed = Math.min(maxSpeed * 0.5, homeDist * 0.8);
            if (homeDist > 1e-6) { mvx = hx / homeDist * speed; mvy = hy / homeDist * speed; }
            else                 { mvx = 0; mvy = 0; }
            aim = speed > 1.0 ? Math.atan2(mvy, mvx) : e.getAngle();
        }

        // keep ships from stacking on each other
        for (EnemyShip o : enemies) {
            if (o == e) continue;
            double sx = e.getX() - o.getX(), sy = e.getY() - o.getY();
            double d = Math.hypot(sx, sy);
            double minD = (e.getRadius() + o.getRadius()) * 3.0 + 1.0;
            if (d < minD && d > 1e-6) {
                double push = (minD - d) / minD * baseMax;
                mvx += sx / d * push;
                mvy += sy / d * push;
            }
        }
        // near the edge of the zone, steer back toward the planet
        double ex = e.getX() - sectorPlanet.getX(), ey = e.getY() - sectorPlanet.getY();
        double ed = Math.max(1e-6, Math.hypot(ex, ey));
        double over = ed - (tether - 20.0);
        if (over > 0) {
            double pull = Math.min(1.0, over / 20.0) * baseMax;
            mvx -= ex / ed * pull;
            mvy -= ey / ed * pull;
        }
        double sp = Math.hypot(mvx, mvy);
        if (sp > maxSpeed) { mvx *= maxSpeed / sp; mvy *= maxSpeed / sp; }

        // turn: normal speed, or the (slow) beam-tracking speed while the beam is on
        double turnRate = (ls == LaserState.FIRING ? sector.beamTrack() * 0.6 : TURN_SPEED)
                * (k.isBoss() ? 0.6 : 1.0);
        double diff = normalize(aim - e.getAngle());
        double step = Math.max(-turnRate * dt, Math.min(turnRate * dt, diff));
        e.setAngle(e.getAngle() + step);

        // accelerate toward the desired velocity
        double accel = ACCEL * Math.max(1.0, maxSpeed / baseMax);
        double ax = mvx - e.getVx(), ay = mvy - e.getVy();
        double al = Math.hypot(ax, ay);
        double maxStep = accel * dt;
        e.setThrusting(al > 0.5);
        if (al > maxStep) { ax *= maxStep / al; ay *= maxStep / al; }
        e.setVx(e.getVx() + ax);
        e.setVy(e.getVy() + ay);

        // weapons
        updateLaser(e, ship, hunting, dist, dt);

        double tol = k.isBoss() ? 0.3 : AIM_TOLERANCE;
        if (hunting && e.getLaserState() == LaserState.IDLE && e.getFireCooldown() <= 0
                && dist <= k.reach * 0.9
                && Math.abs(normalize(aim - e.getAngle())) <= tol) {
            firePlasma(e, acc);
        }
        if (k == EnemyKind.BOSS_FINAL) finalBossPowers(e, hunting, dt);
    }

    // ── Plasma ───────────────────────────────────────────────────────────

    private void firePlasma(EnemyShip e, double acc) {
        EnemyKind k = e.getKind();
        int n = shotsPerVolley(e);
        double spread = 0.01 + 0.14 * (1.0 - acc);          // radians of random error
        double base = e.getAngle() + (rng.nextDouble() - 0.5) * 2 * spread;
        double[] m = e.toWorld(EnemyShip.MUZZLE_X, EnemyShip.MUZZLE_Y);
        double life = k.reach / BULLET_SPEED;

        for (int i = 0; i < n; i++) {
            double a = base + (i - (n - 1) / 2.0) * 0.12;   // fan for bosses
            double vx = Math.cos(a) * BULLET_SPEED + e.getVx();
            double vy = Math.sin(a) * BULLET_SPEED + e.getVy();
            combat.getBullets().add(
                    Bullet.enemyPlasma(m[0], m[1], vx, vy, a, life).withDamage(k.bulletDamage));
        }
        e.setFireCooldown(cooldownFor(e));
        e.setOpenerReady(false);
        e.flashMuzzle();
    }

    private int shotsPerVolley(EnemyShip e) {
        return switch (e.getKind()) {
            case GRUNT       -> 1;
            case BOSS_LOW    -> 3;
            case BOSS_MID    -> 5;
            case BOSS_FINAL  -> 1 + 2 * e.getPhase();      // 3 / 5 / 7
        };
    }

    // how long a ship keeps shooting at its old firing solution before it re-aims
    private double reactionFor(EnemyShip e) {
        return switch (e.getKind()) {
            case GRUNT       -> 2.2 - 1.4 * sector.accuracy() + rng.nextDouble() * 0.4;
            case BOSS_LOW    -> 1.1;
            case BOSS_MID    -> 1.0;
            case BOSS_FINAL  -> e.getPhase() >= 3 ? 0.6 : 0.9;
        };
    }

    private double cooldownFor(EnemyShip e) {
        return switch (e.getKind()) {
            case GRUNT       -> 1.6 - 0.6 * sector.accuracy() + rng.nextDouble() * 0.3;
            case BOSS_LOW    -> 1.3;
            case BOSS_MID    -> 1.0;
            case BOSS_FINAL  -> e.getPhase() == 1 ? 1.0 : (e.getPhase() == 2 ? 0.85 : 0.7);
        };
    }

    // final boss, phase 2+: a ring of slow bolts you have to thread through
    private void nova(EnemyShip e) {
        int n = 24;
        double off = rng.nextDouble() * Math.PI * 2;
        double speed = 20.0;
        for (int i = 0; i < n; i++) {
            double a = off + i * Math.PI * 2 / n;
            double bx = e.getX() + Math.cos(a) * e.getRadius();
            double by = e.getY() + Math.sin(a) * e.getRadius();
            combat.getBullets().add(Bullet.enemyPlasma(bx, by,
                    Math.cos(a) * speed, Math.sin(a) * speed, a, 60.0 / speed).withDamage(8));
        }
        e.setSpecialCooldown(e.getPhase() >= 3 ? 6.5 : 10.0);
        e.flashMuzzle();
    }

    // ── Laser: charge -> fire (follows you slowly) -> cooldown ───────────

    // ── THE ANNIHILATOR's extra powers ───────────────────────────────────
    //   plasma fan (3 / 5 / 7 bolts)   every phase
    //   spiral barrage                 every phase: 1 / 2 / 3 rotating arms of slow bolts
    //   laser                          phase 2: one beam   phase 3: TRIPLE beam
    //   nova ring                      phase 2+
    //   reinforcements                 4 escorts on entering phase 2 and again on phase 3
    //   enrage                         +25% speed in phase 3
    // When it dies its escorts go down with it.

    private static final double SPIRAL_STEP   = 0.09;   // seconds between bolt rows
    private static final double SPIRAL_TURN   = 0.38;   // rad the arms rotate per row
    private static final double SPIRAL_SPEED  = 18.0;
    private static final int    SPIRAL_DAMAGE = 7;
    private static final int    MAX_ESCORTS   = 8;

    private void finalBossPowers(EnemyShip e, boolean hunting, double dt) {
        int phase = e.getPhase();

        if (phase > e.getLastPhase()) {                    // phase change: call for backup
            e.setLastPhase(phase);
            spawnEscorts(e, 4);
        }
        if (!hunting) return;

        if (phase >= 2 && e.getSpecialCooldown() <= 0) nova(e);

        if (e.isSpiraling()) {
            int rows = e.spiralTick(dt, SPIRAL_STEP);
            for (int r = 0; r < rows; r++) {
                double base = e.advanceSpiral(SPIRAL_TURN);
                for (int arm = 0; arm < phase; arm++) {
                    double a = base + arm * Math.PI * 2 / phase;
                    double bx = e.getX() + Math.cos(a) * e.getRadius();
                    double by = e.getY() + Math.sin(a) * e.getRadius();
                    combat.getBullets().add(Bullet.enemyPlasma(bx, by,
                            Math.cos(a) * SPIRAL_SPEED, Math.sin(a) * SPIRAL_SPEED, a,
                            55.0 / SPIRAL_SPEED).withDamage(SPIRAL_DAMAGE));
                }
            }
            if (!e.isSpiraling()) {
                e.setSpiralCooldown(phase >= 3 ? 7.0 : (phase == 2 ? 9.0 : 11.0));
            }
        } else if (e.getSpiralCooldown() <= 0) {
            e.startSpiral(3.0);
        }
    }

    private void spawnEscorts(EnemyShip boss, int count) {
        if (sectorPlanet == null) return;
        int alive = pendingSpawns.size();
        for (EnemyShip o : enemies) if (o.getKind() == EnemyKind.GRUNT && o.isAlive()) alive++;
        for (int i = 0; i < count && alive < MAX_ESCORTS; i++, alive++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = boss.getRadius() * 1.5 + 3.0;
            double x = boss.getX() + Math.cos(a) * r;
            double y = boss.getY() + Math.sin(a) * r;
            EnemyShip m = new EnemyShip(EnemyKind.GRUNT, x, y, a, rng.nextBoolean() ? 1 : -1);
            double hx = x - sectorPlanet.getX(), hy = y - sectorPlanet.getY();
            m.setHome(Math.atan2(hy, hx), Math.hypot(hx, hy),
                    (rng.nextBoolean() ? 1 : -1) * PATROL_RATE);
            m.setSlot(rng.nextDouble() * Math.PI * 2);
            m.setRangeBias(-4 + rng.nextDouble() * 10);
            m.scaleHealth(1.0 + 0.15 * (spawnedLevel - 1));
            m.flashMuzzle();
            pendingSpawns.add(m);
        }
    }

    private int beamCountFor(EnemyShip e) {
        return e.getKind() == EnemyKind.BOSS_FINAL && e.getPhase() >= 3 ? 3 : 1;
    }

    private void updateLaser(EnemyShip e, Ship ship, boolean hunting, double dist, double dt) {
        if (!e.isLaserCapable()) return;
        EnemyKind k = e.getKind();

        switch (e.getLaserState()) {
            case IDLE -> {
                boolean allowed = k != EnemyKind.BOSS_FINAL || e.getPhase() >= 2;
                if (hunting && allowed && e.getLaserCooldown() <= 0 && dist <= k.reach * 0.85) {
                    e.startCharge(sector.laserCharge() * k.chargeMult, beamCountFor(e));
                }
            }
            case CHARGING -> {
                e.addLaserTimer(dt);
                if (!hunting || dist > k.reach) {
                    e.endLaser(2.0);                       // lost the target: fizzle out
                } else if (e.getLaserTimer() >= e.getChargeTime()) {
                    e.startFiring();
                }
            }
            case FIRING -> {
                e.addLaserTimer(-dt);
                if (e.getLaserTimer() <= 0) {
                    e.endLaser(laserCooldownFor(e));
                    return;
                }
                double[] m = e.toWorld(EnemyShip.MUZZLE_X, EnemyShip.MUZZLE_Y);
                int beams = e.getBeamCount();
                for (int i = 0; i < beams; i++) {
                    double a = e.getAngle() + EnemyShip.beamOffset(i, beams);
                    double cx = Math.cos(a), cy = Math.sin(a);
                    double len = clipRay(m[0], m[1], cx, cy, k.reach, ship); // planets block except interior shots
                    e.setBeamLength(i, len);

                    if (ship.getHealth() > 0 && segmentHitsCircle(m[0], m[1],
                            m[0] + cx * len, m[1] + cy * len, ship.getX(), ship.getY(),
                            ShipGeometry.COLLISION_RADIUS + e.getBeamHalfWidth())) {
                        damagePlayer(ship, sector.laserDps() * k.dpsMult * dt);
                    }
                }
            }
        }
    }

    private double laserCooldownFor(EnemyShip e) {
        return switch (e.getKind()) {
            case GRUNT       -> 6.0 + rng.nextDouble() * 4.0;
            case BOSS_LOW    -> 7.0;
            case BOSS_MID    -> 5.5;
            case BOSS_FINAL  -> e.getPhase() >= 3 ? 4.0 : 6.0;
        };
    }

    // shortest distance along a ray before it hits the Sun or a planet
    private double clipRay(double ox, double oy, double dirX, double dirY, double maxLen, Ship ship) {
        double best = maxLen;
        for (CelestialBody body : solids) {
            // Lasers ignore a planet when either the firing ship or the player is inside it.
            if (bodyContains(body, ox, oy) || bodyContains(body, ship.getX(), ship.getY())) continue;

            double fx = ox - body.getX(), fy = oy - body.getY();
            double r = body.getRadius();
            double c = fx * fx + fy * fy - r * r;
            if (c <= 0) return 0;
            double bq = fx * dirX + fy * dirY;
            double disc = bq * bq - c;
            if (disc < 0) continue;
            double t = -bq - Math.sqrt(disc);
            if (t >= 0 && t < best) best = t;
        }
        return best;
    }

    private boolean bodyContains(CelestialBody body, double x, double y) {
        double dx = x - body.getX();
        double dy = y - body.getY();
        return dx * dx + dy * dy <= body.getRadius() * body.getRadius();
    }

    // ── Tether: a ship can never be further than this from its planet ───

    private double tetherRadius(EnemyShip e) {
        double base = sectorPlanet.getGravityRadius()
                + (e.getKind().isBoss() ? TETHER_BOSS : TETHER_GRUNT);
        return sector.persistent() ? base + TETHER_PERSISTENT_EXTRA : base;
    }

    private void tether(EnemyShip e) {
        if (sectorPlanet == null) return;
        double dx = e.getX() - sectorPlanet.getX();
        double dy = e.getY() - sectorPlanet.getY();
        double d  = Math.hypot(dx, dy);
        double max = tetherRadius(e);
        if (d <= max || d < 1e-6) return;
        double nx = dx / d, ny = dy / d;
        e.setX(sectorPlanet.getX() + nx * max);
        e.setY(sectorPlanet.getY() + ny * max);
        double vn = e.getVx() * nx + e.getVy() * ny;
        if (vn > 0) {                       // cancel the outward part of the velocity
            e.setVx(e.getVx() - vn * nx);
            e.setVy(e.getVy() - vn * ny);
        }
    }

    // ── Collisions with bodies ───────────────────────────────────────────

    private void pushOutOfBodies(EnemyShip e) {
        for (CelestialBody body : solids) {
            double dx = e.getX() - body.getX();
            double dy = e.getY() - body.getY();
            double d  = Math.hypot(dx, dy);
            double min = body.getRadius() + e.getRadius();
            if (d >= min || d < 1e-6) continue;

            double nx = dx / d, ny = dy / d;
            e.setX(body.getX() + nx * min);
            e.setY(body.getY() + ny * min);
            double vn = e.getVx() * nx + e.getVy() * ny;
            if (vn < 0) {
                e.setVx(e.getVx() - vn * nx);
                e.setVy(e.getVy() - vn * ny);
            }
        }
    }

    // ── Hits ─────────────────────────────────────────────────────────────

    private void resolveBulletHits(Ship ship, double dt) {
        double playerR = ShipGeometry.COLLISION_RADIUS + BULLET_RADIUS;

        Iterator<Bullet> it = combat.getBullets().iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            if (b.isRemote()) continue;   // enemies are local to each game instance

            if (b.isLaser()) {
                applyPlayerBeam(b, dt);
                continue;
            }

            // path travelled this frame (segment test so fast bullets can't tunnel)
            double x1 = b.getX(), y1 = b.getY();
            double x0 = x1 - b.getVx() * dt, y0 = y1 - b.getVy() * dt;

            if (b.isEnemy()) {
                if (ship.getHealth() > 0
                        && segmentHitsCircle(x0, y0, x1, y1, ship.getX(), ship.getY(), playerR)) {
                    damagePlayer(ship, b.getDamage() > 0 ? b.getDamage() : 8);
                    combat.getImpacts().add(new Impact(x1, y1, b.getAngle()));
                    it.remove();
                }
            } else {
                for (EnemyShip e : enemies) {
                    if (!e.isAlive()) continue;
                    if (segmentHitsCircle(x0, y0, x1, y1, e.getX(), e.getY(),
                            e.getRadius() + BULLET_RADIUS)) {
                        hurtEnemy(e, PLAYER_BULLET_DAMAGE * levelService.getDamageMultiplier());
                        combat.getImpacts().add(new Impact(x1, y1, b.getAngle()));
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    // the player's beam is already clipped by planets (CombatService.clipBeam)
    private void applyPlayerBeam(Bullet b, double dt) {
        if (b.getEnvelope() < 0.2) return;
        double x0 = b.getX(), y0 = b.getY();
        double x1 = x0 + Math.cos(b.getAngle()) * b.getBeamLength();
        double y1 = y0 + Math.sin(b.getAngle()) * b.getBeamLength();
        double dps = (LASER_DPS_BASE + LASER_DPS_CHARGE * b.getChargeLevel())
                * levelService.getDamageMultiplier();

        for (EnemyShip e : enemies) {
            if (e.isAlive() && segmentHitsCircle(x0, y0, x1, y1, e.getX(), e.getY(),
                    e.getRadius() + b.getBeamHalfWidth())) {
                hurtEnemy(e, dps * dt);
            }
        }
    }

    private void hurtEnemy(EnemyShip e, double amount) {
        e.damage(amount);
        if (sector != null && sector.shareAlert()) alertTimer = ALERT_TIME;   // gang up
    }

    // health is an int, beams deal fractions per frame, so carry the remainder
    private void damagePlayer(Ship ship, double amount) {
        playerDmgCarry += amount * levelService.getArmorMultiplier();
        int whole = (int) playerDmgCarry;
        if (whole <= 0) return;
        playerDmgCarry -= whole;
        int hp = Math.max(0, ship.getHealth() - whole);
        ship.setHealth(hp);
        if (hp == 0) playerKilled = true;
    }

    private void removeDead() {
        boolean finalDown = false;
        for (EnemyShip o : enemies) if (o.getKind() == EnemyKind.BOSS_FINAL && !o.isAlive()) finalDown = true;
        if (finalDown) {                                  // the escorts go down with the boss
            for (EnemyShip o : enemies) if (o.isAlive()) o.damage(1e9);
        }
        Iterator<EnemyShip> it = enemies.iterator();
        while (it.hasNext()) {
            EnemyShip e = it.next();
            if (e.isAlive()) continue;
            explode(e);
            if (e.getKind() == EnemyKind.BOSS_FINAL) finalBossDefeated = true;
            it.remove();
        }
    }

    private void explode(EnemyShip e) {
        int n = e.getKind().isBoss() ? 40 : 10;
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = rng.nextDouble() * e.getRadius() * 1.5;
            combat.getImpacts().add(new Impact(
                    e.getX() + Math.cos(a) * d, e.getY() + Math.sin(a) * d, a));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static boolean segmentHitsCircle(double x0, double y0, double x1, double y1,
                                             double cx, double cy, double r) {
        double dx = x1 - x0, dy = y1 - y0;
        double l2 = dx * dx + dy * dy;
        double t  = l2 < 1e-12 ? 0 : ((cx - x0) * dx + (cy - y0) * dy) / l2;
        t = Math.max(0, Math.min(1, t));
        double px = x0 + dx * t - cx;
        double py = y0 + dy * t - cy;
        return px * px + py * py <= r * r;
    }

    private static double normalize(double a) {
        while (a >  Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    // ── Getters (HUD / level progression can use these) ──────────────────

    public List<EnemyShip> getEnemies()        { return enemies; }
    public EnemySector     getSector()         { return sector; }
    public int             getAliveCount()     { return enemies.size(); }
    public int             getSectorTotal()    { return sector == null ? 0 : sector.total(); }
    public boolean         isSectorCleared()   { return sector != null && enemies.isEmpty(); }
    public boolean         isFinalBossDefeated() { return finalBossDefeated; }
    /** True if the hit that took the player's health to 0 came from an enemy. */
    public boolean         killedPlayer()      { return playerKilled; }

    /** Returns true exactly once per sector, the moment its last enemy dies. */
    public boolean consumeSectorCleared() {
        if (isSectorCleared() && !clearedReported) { clearedReported = true; return true; }
        return false;
    }
}