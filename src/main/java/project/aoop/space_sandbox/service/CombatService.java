package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.entity.Bullet;
import project.aoop.space_sandbox.entity.CelestialBody;
import project.aoop.space_sandbox.entity.Impact;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.entity.Star;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

@Service
public class CombatService {

    private static final double CHARGE_TIME         = 10.0;  // seconds to 100%
    private static final double MIN_CHARGE          = 0.1;   // below this the shot is wasted
    private static final double BULLET_RANGE        = 45.0;  // world units a bullet travels
    private static final double BULLET_MIN_SPEED    = 20.0;
    private static final double BULLET_SPEED_FACTOR = 1.6;   // x the level's ship speed limit

    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Impact> impacts = new ArrayList<>();
    private final List<CelestialBody> solids = new ArrayList<>();   // Sun + every planet
    private final LevelService levelService;

    private double  shootCooldown = 0;
    private double  bulletFlash   = 0;     // muzzle flash, 1 -> 0
    private double  chargeLevel   = 0;
    private boolean charging      = false;
    private boolean laserMode     = false;
    private boolean firing        = false; // set by the mouse, consumed in update()
    private Consumer<Bullet> fireListener = bullet -> {};

    public void setFireListener(Consumer<Bullet> listener) {
        fireListener = listener == null ? bullet -> {} : listener;
    }

    public CombatService(LevelService levelService, Star star, List<Planet> planets) {
        this.levelService = levelService;
        solids.add(star);
        solids.addAll(planets);
    }

    // ── Mouse events (called by GameLoop) ────────────────────────────────

    public void onMousePressed(Ship ship) {
        // one beam at a time
        if (shouldUseLaser() && !laserActive()) {
            charging    = true;
            chargeLevel = 0;
        }
    }

    public void onMouseHeld(Ship ship, double dt) {
        if (shouldUseLaser()) {
            if (charging) chargeLevel = Math.min(1.0, chargeLevel + dt / CHARGE_TIME);
        } else {
            firing = true;   // the shot is spawned in update(), after the ship has moved
        }
    }

    public void onMouseReleased(Ship ship) {
        if (shouldUseLaser() && charging) {
            if (chargeLevel >= MIN_CHARGE) fireChargedLaser(ship);
            else System.out.println("⚡ Charge too low — hold longer");
            charging    = false;
            chargeLevel = 0;
        }
    }

    // ── Firing ───────────────────────────────────────────────────────────

    private void trySpawnBullet(Ship ship) {
        if (shootCooldown > 0) return;

        double[] m     = ShipGeometry.toWorld(ship,
                ShipGeometry.BULLET_MUZZLE_X, ShipGeometry.BULLET_MUZZLE_Y);
        double   speed = bulletSpeed();          // speed relative to the ship
        double   a     = ship.getAngle();

        // Inherit the ship's velocity: the camera moves with the ship, so on screen
        // the bullet leaves the nozzle straight even while you drift
        double vx = Math.cos(a) * speed + ship.getVx();
        double vy = Math.sin(a) * speed + ship.getVy();

        Bullet bullet = Bullet.plasma(m[0], m[1], vx, vy, a, BULLET_RANGE / speed);
        bullets.add(bullet);
        fireListener.accept(bullet);
        shootCooldown = levelService.getFireRateCooldown();
        bulletFlash   = 1.0;
    }

    private void fireChargedLaser(Ship ship) {
        double[] m = ShipGeometry.toWorld(ship,
                ShipGeometry.LASER_MUZZLE_X, ShipGeometry.LASER_MUZZLE_Y);
        Bullet bullet = Bullet.laser(m[0], m[1], ship.getAngle(), chargeLevel);
        bullets.add(bullet);
        fireListener.accept(bullet);
        System.out.println("⚡ Laser fired at " + (int) (chargeLevel * 100) + "% charge");
    }

    // Bullet speed relative to the ship, scaled with the level's speed limit
    private double bulletSpeed() {
        return Math.max(BULLET_MIN_SPEED, levelService.getSpeedLimit() * BULLET_SPEED_FACTOR);
    }

    // ── Frame update (call AFTER the ship has moved) ─────────────────────

    public void update(double dt, Ship ship) {
        if (shootCooldown > 0) shootCooldown -= dt;
        if (bulletFlash > 0)   bulletFlash = Math.max(0, bulletFlash - dt * 8.0);

        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            double prevX = b.getX(), prevY = b.getY();
            b.update(dt, ship);

            if (b.isLaser()) {
                clipBeam(b);                          // stop the beam at the first planet
            } else if (hitSolid(b, prevX, prevY)) {   // bullet reached a planet / the Sun
                it.remove();
                continue;
            }
            if (b.isExpired()) it.remove();
        }

        // Spawn AFTER moving the existing bullets, from the ship's final position
        // this frame, so the new bullet appears exactly at the barrel
        if (firing) {
            trySpawnBullet(ship);
            firing = false;
        }

        Iterator<Impact> ii = impacts.iterator();
        while (ii.hasNext()) {
            Impact im = ii.next();
            im.update(dt);
            if (im.isDead()) ii.remove();
        }
    }

    // ── Blocking ─────────────────────────────────────────────────────────

    // Did the bullet's path this frame (prev -> now) cross a body?
    // Segment-vs-circle test, so fast bullets can't tunnel through small planets.
    private boolean hitSolid(Bullet b, double x0, double y0) {
        double x1 = b.getX(), y1 = b.getY();
        double dx = x1 - x0,  dy = y1 - y0;
        double a  = dx * dx + dy * dy;
        double bestT = Double.MAX_VALUE;

        for (CelestialBody body : solids) {
            double fx = x0 - body.getX();
            double fy = y0 - body.getY();
            double r  = body.getRadius();
            double c  = fx * fx + fy * fy - r * r;

            double t;
            if (c <= 0) continue;                       // shots pass through containing bodies
            if (a < 1e-12) continue;
            double bq   = 2 * (fx * dx + fy * dy);
            double disc = bq * bq - 4 * a * c;
            if (disc < 0) continue;
            t = (-bq - Math.sqrt(disc)) / (2 * a);
            if (t < 0 || t > 1) continue;
            if (t < bestT) bestT = t;
        }

        if (bestT == Double.MAX_VALUE) return false;
        impacts.add(new Impact(x0 + dx * bestT, y0 + dy * bestT, b.getAngle()));
        return true;
    }

    // Shorten the beam to the nearest body along its direction (ray vs circle)
    private void clipBeam(Bullet b) {
        double dirX = Math.cos(b.getAngle());
        double dirY = Math.sin(b.getAngle());
        double best = b.getMaxBeamLength();

        for (CelestialBody body : solids) {
            double fx = b.getX() - body.getX();
            double fy = b.getY() - body.getY();
            double r  = body.getRadius();
            double c  = fx * fx + fy * fy - r * r;
            if (c <= 0) continue;                       // beam passes through containing bodies

            double bq   = fx * dirX + fy * dirY;
            double disc = bq * bq - c;
            if (disc < 0) continue;
            double t = -bq - Math.sqrt(disc);
            if (t >= 0 && t < best) best = t;
        }
        b.setBeamLength(best);
    }

    // ── Weapon mode ──────────────────────────────────────────────────────

    public void toggleWeaponMode() {
        if (levelService.hasLaser()) {
            laserMode   = !laserMode;
            charging    = false;
            chargeLevel = 0;
            firing      = false;
            System.out.println("Weapon: " + (laserMode ? "LASER" : "BULLET"));
        }
    }

    private boolean shouldUseLaser() { return levelService.hasLaser() && laserMode; }

    private boolean laserActive() {
        for (Bullet b : bullets) if (b.isLaser()) return true;
        return false;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public List<Bullet> getBullets()     { return bullets; }
    public List<Impact> getImpacts()     { return impacts; }
    public double       getCharge()      { return chargeLevel; }
    public double       getBulletFlash() { return bulletFlash; }
    public boolean      isCharging()     { return charging; }
    public boolean      isLaserMode()    { return shouldUseLaser(); }
    public boolean      isFullyCharged() { return chargeLevel >= 1.0; }

    public void spawnRemotePlasma(double x, double y, double vx, double vy,
                                  double angle, double lifetime) {
        bullets.add(Bullet.plasma(x, y, vx, vy, angle, lifetime));
    }

    public void spawnRemoteBeam(double chargeLevel) {
        bullets.add(Bullet.laser(0, 0, 0, Math.max(0, Math.min(1, chargeLevel))));
    }

    public void clearRemoteBullets() {
        bullets.removeIf(Bullet::isLaser);
    }
}