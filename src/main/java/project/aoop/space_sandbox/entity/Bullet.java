package project.aoop.space_sandbox.entity;

public class Bullet {

    public enum Team { PLAYER, ENEMY }

    private static final double FADE_IN  = 0.08;
    private static final double FADE_OUT = 0.45;

    private double x, y;
    private double vx, vy;
    private double angle;
    private double lifetime;
    private final double  maxLifetime;
    private final boolean laser;
    private final double  chargeLevel;     // 0..1, lasers only
    private boolean remote = false;
    private Team team = Team.PLAYER;
    private final double maxBeamLength;    // world units
    private double beamLength;             // current length, shortened when blocked
    private int damage = 0;

    private Bullet(double x, double y, double vx, double vy, double angle,
                   double lifetime, boolean laser, double chargeLevel) {
        this.x = x;  this.y = y;
        this.vx = vx; this.vy = vy;
        this.angle = angle;
        this.lifetime = lifetime;
        this.maxLifetime = lifetime;
        this.laser = laser;
        this.chargeLevel = chargeLevel;
        this.maxBeamLength = laser ? 14.0 + 34.0 * chargeLevel : 0;
        this.beamLength = maxBeamLength;
    }

    /** Plasma bullet. `angle` is the heading at fire time (used for drawing). */
    public static Bullet plasma(double x, double y, double vx, double vy,
                                double angle, double lifetime) {
        return new Bullet(x, y, vx, vy, angle, lifetime, false, 0);
    }

    public static Bullet enemyPlasma(double x, double y, double vx, double vy,
                                     double angle, double lifetime) {
        Bullet b = plasma(x, y, vx, vy, angle, lifetime);
        b.team = Team.ENEMY;
        return b;
    }

    /** Charged laser: a beam attached to the laser barrel for charge% x 10 seconds. */
    public static Bullet laser(double x, double y, double angle, double chargeLevel) {
        return new Bullet(x, y, Math.cos(angle), Math.sin(angle), angle,
                chargeLevel * 10.0, true, chargeLevel);
    }

    public void update(double dt, Ship ship) {
        if (laser) {
            // stay glued to the laser barrel and follow the ship's aim
            double[] m = ShipGeometry.toWorld(ship,
                    ShipGeometry.LASER_MUZZLE_X, ShipGeometry.LASER_MUZZLE_Y);
            x = m[0];
            y = m[1];
            angle = ship.getAngle();
            vx = Math.cos(angle);
            vy = Math.sin(angle);
        } else {
            x += vx * dt;
            y += vy * dt;
        }
        lifetime -= dt;
    }

    /** 0..1 brightness: quick fade-in, fade-out over the last moments. */
    public double getEnvelope() {
        double age = maxLifetime - lifetime;
        double in  = Math.min(1.0, age / FADE_IN);
        double out = Math.min(1.0, lifetime / FADE_OUT);
        return Math.max(0, in * out);
    }

    // ── beam length (shortened by CombatService when a planet is in the way) ──
    public double  getMaxBeamLength() { return maxBeamLength; }
    public double  getBeamLength()    { return beamLength; }
    public void    setBeamLength(double len) {
        beamLength = Math.max(0, Math.min(maxBeamLength, len));
    }
    public boolean isBeamBlocked()    { return laser && beamLength < maxBeamLength - 1e-6; }

    /** Beam half-width in world units (thicker with more charge). */
    public double getBeamHalfWidth()  { return 0.08 + 0.26 * chargeLevel; }

    public boolean isExpired()        { return lifetime <= 0; }
    public boolean isLaser()          { return laser; }
    public double  getChargeLevel()   { return chargeLevel; }
    public double  getX()             { return x; }
    public double  getY()             { return y; }
    public double  getVx()            { return vx; }
    public double  getVy()            { return vy; }
    public double  getAngle()         { return angle; }
    public double  getLifetime()      { return lifetime; }
    public Team    getTeam()          { return team; }
    public boolean isEnemy()          { return team == Team.ENEMY; }
    public Bullet markRemote()        { this.remote = true; return this; }
    public boolean isRemote()         { return remote; }
    public Bullet withDamage(int d)   { this.damage = d; return this; }
    public int  getDamage()           { return damage; }
}