package project.aoop.space_sandbox.entity;

/**
 * A hostile ship (grunt or boss). Data + bookkeeping only; the brain is EnemyService.
 * Design space is the same as the player's ship: nose = -y, +x = right wing.
 * Every design has its cannon tip at (MUZZLE_X, MUZZLE_Y); plasma and lasers leave from there.
 */
public class EnemyShip {

    public static final double WORLD_PER_UNIT = 0.10;
    public static final double BASE_RADIUS    = 8.0 * WORLD_PER_UNIT;
    public static final double MUZZLE_X       = 0.0;
    public static final double MUZZLE_Y       = -11.0;
    public static final double BEAM_DURATION  = 1.2;    // seconds a beam stays on
    public static final double BEAM_SPREAD    = 0.38;   // rad between the beams of a triple laser

    public enum LaserState { IDLE, CHARGING, FIRING }

    /** Angle of beam i (of n) relative to the ship's heading. */
    public static double beamOffset(int i, int n) {
        return n <= 1 ? 0.0 : (i - (n - 1) / 2.0) * BEAM_SPREAD;
    }

    private final EnemyKind kind;
    private double maxHealth;
    private double health;

    private double x, y, vx, vy, angle;
    private final int strafeDir;          // +1 / -1: which way it circles the player
    private double rangeBias;             // spreads ships out so they don't stack
    private double slot;                  // angle of this ship's slot when surrounding the player

    // idle patrol around the home planet
    private double homeAngle, homeRadius, patrolRate;

    private double  fireCooldown    = 1.0;
    private double  specialCooldown = 6.0;
    private double  hitFlash        = 0;
    private double  muzzleFlash     = 0;
    private boolean thrusting       = false;
    private boolean openerReady     = true;

    // plasma firing solution: the intercept point it is aiming at, refreshed only now and then
    private double  aimX, aimY, aimRefresh;

    // laser lock: direction frozen for the last second of the charge
    private boolean locked;
    private double  lockAngle;

    // laser (a boss can fire up to 3 beams at once)
    private boolean    laserCapable;
    private LaserState laserState = LaserState.IDLE;
    private double     laserTimer;        // CHARGING: seconds charged. FIRING: seconds left
    private double     laserCooldown;
    private double     chargeTime;
    private int        beamCount = 1;
    private final double[] beamLengths = new double[3];

    // final-boss powers
    private int    lastPhase = 1;         // to detect phase changes (reinforcements)
    private double spiralTime;            // seconds of spiral barrage left
    private double spiralAccum;
    private double spiralAngle;
    private double spiralCooldown = 5.0;

    public EnemyShip(EnemyKind kind, double x, double y, double angle, int strafeDir) {
        this.kind = kind;
        this.maxHealth = kind.health;
        this.health = kind.health;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.strafeDir = strafeDir;
    }

    // ── per-frame bookkeeping ────────────────────────────────────────────

    public void tick(double dt) {
        if (fireCooldown > 0)    fireCooldown    -= dt;
        if (specialCooldown > 0) specialCooldown -= dt;
        if (aimRefresh > 0)      aimRefresh      -= dt;
        if (spiralCooldown > 0 && spiralTime <= 0) spiralCooldown -= dt;
        if (laserState == LaserState.IDLE && laserCooldown > 0) laserCooldown -= dt;
        if (hitFlash > 0)        hitFlash    = Math.max(0, hitFlash - dt * 5.0);
        if (muzzleFlash > 0)     muzzleFlash = Math.max(0, muzzleFlash - dt * 8.0);
    }

    public void move(double dt) { x += vx * dt; y += vy * dt; }

    public void advanceHome(double dt) { homeAngle += patrolRate * dt; }

    public void damage(double amount) {
        health = Math.max(0, health - amount);
        hitFlash = 1.0;
    }

    /** Scales max and current health (grunts get tougher on later levels). */
    public void scaleHealth(double mult) { maxHealth *= mult; health = maxHealth; }

    /** Converts a point on the enemy (design units) to world coordinates. */
    public double[] toWorld(double ux, double uy) {
        double s = getUnitScale();
        double sin = Math.sin(angle), cos = Math.cos(angle);
        return new double[]{
                x + (-ux * sin - uy * cos) * s,
                y + ( ux * cos - uy * sin) * s
        };
    }

    // ── laser state machine helpers ──────────────────────────────────────

    public void startCharge(double seconds, int beams) {
        laserState = LaserState.CHARGING;
        laserTimer = 0;
        chargeTime = seconds;
        beamCount = Math.max(1, Math.min(3, beams));
        locked = false;
    }
    public void startFiring()               { laserState = LaserState.FIRING; laserTimer = BEAM_DURATION; }
    public void endLaser(double cooldown)   { laserState = LaserState.IDLE; laserTimer = 0; laserCooldown = cooldown; locked = false; }
    public void addLaserTimer(double d)     { laserTimer += d; }

    // ── spiral barrage helpers ───────────────────────────────────────────

    public void startSpiral(double seconds) { spiralTime = seconds; spiralAccum = 0; }
    public boolean isSpiraling()            { return spiralTime > 0; }

    /** Advances the barrage; returns how many bolts-steps are due this frame. */
    public int spiralTick(double dt, double step) {
        spiralTime -= dt;
        spiralAccum += dt;
        int n = 0;
        while (spiralAccum >= step) { spiralAccum -= step; n++; }
        return n;
    }
    public double advanceSpiral(double by)  { spiralAngle += by; return spiralAngle; }

    // ── aiming state ─────────────────────────────────────────────────────

    public void   setAimPoint(double x, double y) { aimX = x; aimY = y; }
    public double getAimX()                       { return aimX; }
    public double getAimY()                       { return aimY; }
    public double getAimRefresh()                 { return aimRefresh; }
    public void   setAimRefresh(double t)         { aimRefresh = t; }
    public void   lockAim(double angle)           { locked = true; lockAngle = angle; }
    public boolean isLocked()                     { return locked; }
    public double getLockAngle()                  { return lockAngle; }

    // ── derived values ───────────────────────────────────────────────────

    public EnemyKind getKind()          { return kind; }
    public double getUnitScale()        { return WORLD_PER_UNIT * kind.scale; }
    public double getRadius()           { return BASE_RADIUS * kind.scale; }
    public double getBeamHalfWidth()    { return 0.06 + 0.14 * kind.scale; }
    public boolean isAlive()            { return health > 0; }
    public double getHealth()           { return health; }
    public double getMaxHealth()        { return maxHealth; }
    public double getHealthFraction()   { return health / maxHealth; }
    /** Boss phase 1..3 from remaining health (>66%, >33%, below). */
    public int    getPhase()            { double f = getHealthFraction(); return f > 0.66 ? 1 : (f > 0.33 ? 2 : 3); }

    // ── plain getters / setters ──────────────────────────────────────────

    public double  getX()               { return x; }
    public double  getY()               { return y; }
    public double  getVx()              { return vx; }
    public double  getVy()              { return vy; }
    public double  getAngle()           { return angle; }
    public int     getStrafeDir()       { return strafeDir; }
    public double  getRangeBias()       { return rangeBias; }
    public double  getSlot()            { return slot; }
    public double  getHomeAngle()       { return homeAngle; }
    public double  getHomeRadius()      { return homeRadius; }
    public double  getFireCooldown()    { return fireCooldown; }
    public double  getSpecialCooldown() { return specialCooldown; }
    public double  getHitFlash()        { return hitFlash; }
    public double  getMuzzleFlash()     { return muzzleFlash; }
    public boolean isThrusting()        { return thrusting; }
    public boolean isOpenerReady()      { return openerReady; }
    public boolean isLaserCapable()     { return laserCapable; }
    public LaserState getLaserState()   { return laserState; }
    public double  getLaserTimer()      { return laserTimer; }
    public double  getLaserCooldown()   { return laserCooldown; }
    public double  getChargeTime()      { return chargeTime; }
    public int     getBeamCount()       { return beamCount; }
    public double  getBeamLength(int i) { return beamLengths[i]; }
    public double  getBeamLength()      { return beamLengths[0]; }
    public int     getLastPhase()       { return lastPhase; }
    public double  getSpiralCooldown()  { return spiralCooldown; }
    public double  getSpiralAngle()     { return spiralAngle; }

    public void setX(double x)                  { this.x = x; }
    public void setY(double y)                  { this.y = y; }
    public void setVx(double vx)                { this.vx = vx; }
    public void setVy(double vy)                { this.vy = vy; }
    public void setAngle(double a)              { this.angle = a; }
    public void setThrusting(boolean t)         { this.thrusting = t; }
    public void setOpenerReady(boolean b)       { this.openerReady = b; }
    public void setLaserCapable(boolean b)      { this.laserCapable = b; }
    public void setLaserCooldown(double c)      { this.laserCooldown = c; }
    public void setBeamLength(int i, double l)  { this.beamLengths[i] = l; }
    public void setFireCooldown(double c)       { this.fireCooldown = c; }
    public void setSpecialCooldown(double c)    { this.specialCooldown = c; }
    public void setSpiralCooldown(double c)     { this.spiralCooldown = c; }
    public void setLastPhase(int p)             { this.lastPhase = p; }
    public void setRangeBias(double b)          { this.rangeBias = b; }
    public void setSlot(double s)               { this.slot = s; }
    public void flashMuzzle()                   { this.muzzleFlash = 1.0; }
    public void setHome(double angle, double radius, double rate) {
        this.homeAngle = angle;
        this.homeRadius = radius;
        this.patrolRate = rate;
    }
}