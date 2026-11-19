package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.entity.Bullet;
import project.aoop.space_sandbox.entity.CelestialBody;
import project.aoop.space_sandbox.entity.Impact;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.RemoteShip;
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
    private static final double FIRE_BUFFER         = 0.15;

    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Impact> impacts = new ArrayList<>();
    private final List<CelestialBody> solids = new ArrayList<>();   // Sun + every planet
    private final LevelService levelService;
    private final RemoteShip   remoteShip;

    private Consumer<Bullet> fireListener;   // co-op: told about every shot WE fire

    private double  shootCooldown = 0;
    private double  bulletFlash   = 0;
    private double  chargeLevel   = 0;
    private boolean charging      = false;
    private boolean laserMode     = false;

    // magazine
    private int     ammo;
    private int     lastCapacity;
    private boolean reloading   = false;
    private double  reloadTimer = 0;
    private double  fireBuffer  = 0;    // a pending shot, waiting for the gun to be ready


    public CombatService(LevelService levelService, Star star, List<Planet> planets,
                         RemoteShip remoteShip) {
        this.levelService = levelService;
        this.remoteShip = remoteShip;
        solids.add(star);
        solids.addAll(planets);
        lastCapacity = levelService.getMagazineSize();
        ammo = lastCapacity;
    }

    public void setFireListener(Consumer<Bullet> listener) { this.fireListener = listener; }

    // ── Mouse events (called by GameLoop) ────────────────────────────────

    public void onMousePressed(Ship ship) {
        if (shouldUseLaser()) {
            // one beam of your own at a time
            if (!laserActive()) {
                charging    = true;
                chargeLevel = 0;
            }
        } else {
            fireBuffer = FIRE_BUFFER;
        }
    }

    public void onMouseHeld(Ship ship, double dt) {
        if (shouldUseLaser() && charging) {
            chargeLevel = Math.min(1.0, chargeLevel + dt / levelService.getLaserChargeTime());
        } else if (!shouldUseLaser()) {
            fireBuffer = FIRE_BUFFER;
        }
    }

    public void onMouseReleased(Ship ship) {
        if (shouldUseLaser() && charging) {
            if (chargeLevel >= MIN_CHARGE) fireChargedLaser(ship);
            else System.out.println("⚡ Charge too low — hold longer");
            charging    = false;
            chargeLevel = 0;
        } else {
            fireBuffer = 0;
        }
    }

    // ── Reload ───────────────────────────────────────────────────────────

    /** R key: reload early. */
    public void reload() {
        startReload();
    }

    private void startReload() {
        if (reloading || ammo >= levelService.getMagazineSize()) return;
        reloading   = true;
        reloadTimer = 0;
    }

    private void updateReload(double dt) {
        if (!reloading) return;
        reloadTimer += dt;
        if (reloadTimer >= levelService.getReloadTime()) {
            ammo        = levelService.getMagazineSize();
            reloading   = false;
            reloadTimer = 0;
        }
    }

    // a level change resizes the magazine: leveling up brings the extra rounds with it
    private void syncMagazine() {
        int capacity = levelService.getMagazineSize();
        if (capacity > lastCapacity) ammo += capacity - lastCapacity;
        ammo = Math.min(ammo, capacity);
        lastCapacity = capacity;
    }

    /** New game: full magazine, nothing in the air. */
    public void resetWeapons() {
        lastCapacity = levelService.getMagazineSize();
        ammo         = lastCapacity;
        reloading    = false;
        reloadTimer  = 0;
        fireBuffer   = 0;
        shootCooldown = 0;
        charging     = false;
        chargeLevel  = 0;
        bullets.clear();
        impacts.clear();
    }

    // ── Firing ───────────────────────────────────────────────────────────

    private void spawnBullet(Ship ship) {
        double[] m     = ShipGeometry.toWorld(ship,
                ShipGeometry.BULLET_MUZZLE_X, ShipGeometry.BULLET_MUZZLE_Y);
        double   speed = bulletSpeed();          // speed relative to the ship
        double   a     = ship.getAngle();

        // Inherit the ship's velocity: the camera moves with the ship, so on screen
        // the bullet leaves the nozzle straight even while you drift
        double vx = Math.cos(a) * speed + ship.getVx();
        double vy = Math.sin(a) * speed + ship.getVy();

        Bullet b = Bullet.plasma(m[0], m[1], vx, vy, a, BULLET_RANGE / speed);
        bullets.add(b);

        ammo--;
        shootCooldown = levelService.getFireRateCooldown();
        bulletFlash   = 1.0;
        notifyFired(b);

        if (ammo <= 0) startReload();            // an empty magazine reloads by itself
    }

    private void fireChargedLaser(Ship ship) {
        double[] m = ShipGeometry.toWorld(ship,
                ShipGeometry.LASER_MUZZLE_X, ShipGeometry.LASER_MUZZLE_Y);
        Bullet b = Bullet.laser(m[0], m[1], ship.getAngle(), chargeLevel);
        bullets.add(b);
        System.out.println("⚡ Laser fired at " + (int) (chargeLevel * 100) + "% charge");
        notifyFired(b);
    }

    private void notifyFired(Bullet b) {
        if (fireListener != null) fireListener.accept(b);
    }

    // Bullet speed relative to the ship, scaled with the level's speed limit
    private double bulletSpeed() {
        return Math.max(BULLET_MIN_SPEED, levelService.getSpeedLimit() * BULLET_SPEED_FACTOR);
    }

    // ── Co-op: shots fired by the partner ────────────────────────────────

    public void spawnRemotePlasma(double x, double y, double vx, double vy,
                                  double angle, double lifetime) {
        bullets.add(Bullet.plasma(x, y, vx, vy, angle, lifetime).markRemote());
    }

    /** The beam's position and direction come from the partner's ship every frame. */
    public void spawnRemoteBeam(double charge) {
        bullets.add(Bullet.laser(0, 0, 0, charge).markRemote());
    }

    public void clearRemoteBullets() {
        bullets.removeIf(Bullet::isRemote);
    }

    // ── Frame update (call AFTER the ship has moved) ─────────────────────

    public void update(double dt, Ship ship) {
        if (shootCooldown > 0) shootCooldown -= dt;
        if (bulletFlash > 0)   bulletFlash = Math.max(0, bulletFlash - dt * 8.0);

        syncMagazine();
        updateReload(dt);

        Ship ghost = remoteShip.getGhost();

        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            double prevX = b.getX(), prevY = b.getY();
            b.update(dt, b.isRemote() ? ghost : ship);   // a partner's beam follows THEIR ship
            Ship owner = b.isRemote() ? ghost : ship;

            if (b.isLaser()) {
                clipBeam(b, owner);                   // stop the beam at the first planet
            } else if (hitSolid(b, prevX, prevY, owner)) { // bullet reached a planet / the Sun
                it.remove();
                continue;
            }
            if (b.isExpired()) it.remove();
        }

        // Spawn AFTER the ship moved, so the shot appears exactly at the barrel.
        // A held click slightly before the gun is ready is remembered for a moment.
        if (fireBuffer > 0) {
            if (!reloading && ammo > 0 && shootCooldown <= 0) {
                spawnBullet(ship);
                fireBuffer = 0;
            } else {
                fireBuffer -= dt;
                if (!reloading && ammo <= 0) startReload();   // clicking on an empty magazine reloads
            }
        }

        Iterator<Impact> ii = impacts.iterator();
        while (ii.hasNext()) {
            Impact im = ii.next();
            im.update(dt);
            if (im.isDead()) ii.remove();
        }
    }

    // ── Blocking ─────────────────────────────────────────────────────────

    private boolean bodyContains(CelestialBody body, double x, double y) {
        double dx = x - body.getX();
        double dy = y - body.getY();
        return dx * dx + dy * dy <= body.getRadius() * body.getRadius();
    }

    // Did the bullet's path this frame (prev -> now) cross a body?
    // Segment-vs-circle test, so fast bullets can't tunnel through small planets.
    private boolean hitSolid(Bullet b, double x0, double y0, Ship owner) {
        double x1 = b.getX(), y1 = b.getY();
        double dx = x1 - x0,  dy = y1 - y0;
        double a  = dx * dx + dy * dy;
        double bestT = Double.MAX_VALUE;

        for (CelestialBody body : solids) {
            // A shot originating from inside a planet (or for an enemy shot, aimed at a
            // player already inside that planet) is allowed to travel through the surface.
            boolean shooterInside = bodyContains(body, x0, y0);
            boolean ownerInside = bodyContains(body, owner.getX(), owner.getY());
            if (ownerInside || (b.isEnemy() && shooterInside)) continue;

            double fx = x0 - body.getX();
            double fy = y0 - body.getY();
            double r  = body.getRadius();
            double c  = fx * fx + fy * fy - r * r;

            double t;
            if (c <= 0) {
                t = 0;                                  // started inside the body
            } else {
                if (a < 1e-12) continue;
                double bq   = 2 * (fx * dx + fy * dy);
                double disc = bq * bq - 4 * a * c;
                if (disc < 0) continue;
                t = (-bq - Math.sqrt(disc)) / (2 * a);
                if (t < 0 || t > 1) continue;
            }
            if (t < bestT) bestT = t;
        }

        if (bestT == Double.MAX_VALUE) return false;
        impacts.add(new Impact(x0 + dx * bestT, y0 + dy * bestT, b.getAngle()));
        return true;
    }

    // Shorten the beam to the nearest body along its direction (ray vs circle)
    private void clipBeam(Bullet b, Ship owner) {
        double dirX = Math.cos(b.getAngle());
        double dirY = Math.sin(b.getAngle());
        double best = b.getMaxBeamLength();

        for (CelestialBody body : solids) {
            if (bodyContains(body, owner.getX(), owner.getY())) continue;

            double fx = b.getX() - body.getX();
            double fy = b.getY() - body.getY();
            double r  = body.getRadius();
            double c  = fx * fx + fy * fy - r * r;
            if (c <= 0) { best = 0; break; }            // muzzle already inside a body

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
            fireBuffer  = 0;
            System.out.println("Weapon: " + (laserMode ? "LASER" : "BULLET"));
        }
    }

    private boolean shouldUseLaser() { return levelService.hasLaser() && laserMode; }

    // only YOUR beams count for the one-at-a-time rule
    private boolean laserActive() {
        for (Bullet b : bullets) if (b.isLaser() && !b.isRemote()) return true;
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

    public int     getAmmo()          { return ammo; }
    public int     getMagazineSize()  { return levelService.getMagazineSize(); }
    public boolean isReloading()      { return reloading; }
    public double  getReloadProgress() {
        return reloading ? Math.min(1.0, reloadTimer / levelService.getReloadTime()) : 0;
    }
}