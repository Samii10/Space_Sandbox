package project.aoop.space_sandbox.entity;

/** The four enemy tiers. Bosses are the same ship model, just bigger and nastier. */
public enum EnemyKind {

    //          title               scale  health   speed prefMin prefMax reach  chargeX dpsX  dmg
    GRUNT      ("",                  1.0,    60.0,  10.0,  18.0,   32.0,   45.0,  1.0,   1.0,  6),
    BOSS_LOW   ("WARDEN",            2.2,   1500.0,   15.0,  30.0,   48.0,   65.0,  0.8,   2.2, 14),
    BOSS_MID   ("DREADNOUGHT",       3.0,  5000.0,   18.0,  34.0,   55.0,   70.0,  0.7,   3.0, 18),
    BOSS_FINAL ("THE ANNIHILATOR",   4.5,  10000.0,  30.0,  40.0,   60.0,   75.0,  0.5,   2.6, 16);

    public final String title;
    public final double scale;        // ship size multiplier
    public final double health;
    public final double maxSpeed;     // before the sector's speed multiplier
    public final double prefMin;      // backs off closer than this
    public final double prefMax;      // closes in beyond this
    public final double reach;        // plasma range AND laser length (world units)
    public final double chargeMult;   // laser charge time multiplier (bosses charge faster)
    public final double dpsMult;      // laser damage multiplier
    public final int    bulletDamage; // damage to one plasma bolt to the player

    EnemyKind(String title, double scale, double health, double maxSpeed,
              double prefMin, double prefMax, double reach,
              double chargeMult, double dpsMult, int bulletDamage) {
        this.title = title;
        this.scale = scale;
        this.health = health;
        this.maxSpeed = maxSpeed;
        this.prefMin = prefMin;
        this.prefMax = prefMax;
        this.reach = reach;
        this.chargeMult = chargeMult;
        this.dpsMult = dpsMult;
        this.bulletDamage = bulletDamage;
    }

    public boolean isBoss() { return this != GRUNT; }
}