package project.aoop.space_sandbox.config;

public class PhysicsConfig {

    // ── Gravity ──────────────────────────────────────────────────────────
    public static final double G                  = 0.1;
    public static final double ORBIT_ASSIST_RATE  = 3.0;  // was 2.0 — snaps to orbit faster
    public static final double RADIAL_DAMPEN_RATE = 0.85; // was 0.2 — stops outward drift fast
    public static final double LOCKED_GRAVITY_MULT = 18.0;

    // ── Ship ─────────────────────────────────────────────────────────────
    public static final double SHIP_THRUST        = 5.6;
    public static final double SHIP_ROTATE_SPEED  = 1.2;
    public static final double SHIP_BRAKE_FORCE   = 4.3;
    public static final double SHIP_START_X       = -208.4; // 75 units outside Venus at its start angle
    public static final double SHIP_START_Y       = 368.4;
    public static final double SHIP_START_VX      = 0.153;  // drifting toward Venus
    public static final double SHIP_START_VY      = -0.265;
    // ── Mouse aiming and strafing ────────────────────────────────────────
    public static final double  TURN_RATE              = 8.0;    // rad/s the ship swings toward the mouse (0 = instant)
    public static final double  STRAFE_POWER           = 0.85;   // A / D acceleration compared with W
    public static final double  REVERSE_POWER          = 0.60;   // S acceleration compared with W
    public static final boolean MOVE_RELATIVE_TO_SHIP = true;    // false: WASD = up / left / down / right on screen

    // ── Movement: W builds speed, letting go coasts down ─────────────────
    public static final double ACCEL_TIME        = 3.0;   // seconds to reach top speed from a standstill
    public static final double COAST_TIME        = 6.0;   // seconds to coast from top speed to a stop

    // ── Boost (Shift) ────────────────────────────────────────────────────
    public static final double BOOST_FACTOR      = 1.5;   // boost speed = level top speed x this (level 1: 8 -> 12 u/s)
    public static final double BOOST_ACCEL_MULT  = 2.5;   // extra acceleration while boosting
    public static final double BOOST_BLEED_TIME  = 2.5;   // seconds to ease from boost speed back to top speed
    public static final double BOOST_DURATION    = 5.0;   // seconds of boost from a full tank
    public static final double BOOST_REFUEL_TIME = 12.0;  // seconds to refill from empty

    // Level-indexed values. Index 0 is unused; current values are preserved
    // for every level because the existing ship physics uses the constants above.
    public static final double[] SPEED_LIMITS = {
            0.0, 8.0, 10.0, 12.0, 28.0, 45.0, 60.0, 75.0, 90.0
    };
    public static final double[] THRUST_POWERS = {
            0.0, SHIP_THRUST, SHIP_THRUST, SHIP_THRUST,
            SHIP_THRUST, SHIP_THRUST, SHIP_THRUST, SHIP_THRUST, SHIP_THRUST
    };

    // ── Camera ───────────────────────────────────────────────────────────
    public static final double CAMERA_ZOOM        = 0.9;

    // ── Sun ──────────────────────────────────────────────────────────────
    public static final double SUN_MASS           = 50000;
    public static final double SUN_RADIUS         = 25.0;
    public static final double SUN_GRAVITY_RADIUS = 140.0;
    public static final double SUN_X              = 0.0;
    public static final double SUN_Y              = 0.0;

    // ── Moon (orbits Earth, not Sun) ─────────────────────────────────────
    public static final double MOON_MASS           = 25;
    public static final double MOON_RADIUS         = 8.0;
    public static final double MOON_ORBIT_RADIUS   = 35.0;  // distance from Earth
    public static final double MOON_PERIOD         = 800;   // seconds per orbit around Earth
    public static final double MOON_GRAVITY_RADIUS = 20.0;
    public static final double MOON_START_ANGLE    = 0.8;

// Remove old Moon semi-major/minor — replaced by MOON_ORBIT_RADIUS

    // ── Earth ────────────────────────────────────────────────────────────
    public static final double EARTH_MASS           = 125;
    public static final double EARTH_RADIUS         = 20.0;
    public static final double EARTH_SEMI_MAJOR     = 500.0;
    public static final double EARTH_SEMI_MINOR     = 499.0;
    public static final double EARTH_PERIOD         = 2000;
    public static final double EARTH_GRAVITY_RADIUS = 50.0;
    public static final double EARTH_START_ANGLE    = 1.57;  // bottom of orbit (0, 499)

    // ── Mercury ──────────────────────────────────────────────────────────
    public static final double MERCURY_MASS           = 25;
    public static final double MERCURY_RADIUS         = 10.0;
    public static final double MERCURY_SEMI_MAJOR     = 200.0;
    public static final double MERCURY_SEMI_MINOR     = 196.0;
    public static final double MERCURY_PERIOD         = 600;
    public static final double MERCURY_GRAVITY_RADIUS = 25.0;
    public static final double MERCURY_START_ANGLE    = 0.5;

    // ── Venus ────────────────────────────────────────────────────────────
    public static final double VENUS_MASS           = 65;
    public static final double VENUS_RADIUS         = 18.0;
    public static final double VENUS_SEMI_MAJOR     = 350.0;
    public static final double VENUS_SEMI_MINOR     = 350.0;
    public static final double VENUS_PERIOD         = 1200;
    public static final double VENUS_GRAVITY_RADIUS = 40.0;
    public static final double VENUS_START_ANGLE    = 2.1;

    // ── Mars ─────────────────────────────────────────────────────────────
    public static final double MARS_MASS           = 45;
    public static final double MARS_RADIUS         = 13.0;
    public static final double MARS_SEMI_MAJOR     = 750.0;
    public static final double MARS_SEMI_MINOR     = 743.0;
    public static final double MARS_PERIOD         = 3800;
    public static final double MARS_GRAVITY_RADIUS = 35.0;
    public static final double MARS_START_ANGLE    = 1.2;

    // ── Jupiter ──────────────────────────────────────────────────────────
    public static final double JUPITER_MASS           = 1000;
    public static final double JUPITER_RADIUS         = 40.0;
    public static final double JUPITER_SEMI_MAJOR     = 2000.0;
    public static final double JUPITER_SEMI_MINOR     = 1997.0;
    public static final double JUPITER_PERIOD         = 12000;
    public static final double JUPITER_GRAVITY_RADIUS = 100.0;
    public static final double JUPITER_START_ANGLE    = 3.8;

    // ── Saturn ───────────────────────────────────────────────────────────
    public static final double SATURN_MASS           = 500;
    public static final double SATURN_RADIUS         = 32.0;
    public static final double SATURN_SEMI_MAJOR     = 3500.0;
    public static final double SATURN_SEMI_MINOR     = 3494.0;
    public static final double SATURN_PERIOD         = 24000;
    public static final double SATURN_GRAVITY_RADIUS = 80.0;
    public static final double SATURN_START_ANGLE    = 0.5;

    // ── Uranus ───────────────────────────────────────────────────────────
    public static final double URANUS_MASS           = 350;
    public static final double URANUS_RADIUS         = 26.0;
    public static final double URANUS_SEMI_MAJOR     = 5000.0;
    public static final double URANUS_SEMI_MINOR     = 4994.0;
    public static final double URANUS_PERIOD         = 48000;
    public static final double URANUS_GRAVITY_RADIUS = 70.0;
    public static final double URANUS_START_ANGLE    = 2.9;

    // ── Neptune ──────────────────────────────────────────────────────────
    public static final double NEPTUNE_MASS           = 350;
    public static final double NEPTUNE_RADIUS         = 25.0;
    public static final double NEPTUNE_SEMI_MAJOR     = 6500.0;
    public static final double NEPTUNE_SEMI_MINOR     = 6494.0;
    public static final double NEPTUNE_PERIOD         = 96000;
    public static final double NEPTUNE_GRAVITY_RADIUS = 70.0;
    public static final double NEPTUNE_START_ANGLE    = 5.1;

    // ── Fire rate cooldown per level (seconds between shots) ─────────────
    // Level 1: slow single shots. Level 8: rapid fire
    public static final double[] FIRE_RATES = {
            0.0,   // index 0 unused
            0.40,  // Level 1 — slow
            0.32,  // Level 2
            0.26,  // Level 3
            0.20,  // Level 4
            0.16,  // Level 5 — laser starts here
            0.12,  // Level 6
            0.10,  // Level 7
            0.08,  // Level 8 — rapid fire
    };

    public static final double[] BRAKE_STOP_TIMES = {
            0.0,   // index 0 unused
            4.0,   // Level 1
            3.5,   // Level 2
            3.0,   // Level 3
            2.5,   // Level 4
            2.0,   // Level 5
            1.6,   // Level 6
            1.3,   // Level 7
            1.0,   // Level 8
    };

    // ── Weapons per level ────────────────────────────────────────────────
// Seconds to charge the laser from 0 to 100% (it unlocks at level 5, and gets quicker after)
    public static final double[] CHARGE_TIMES   = {0, 10.0, 10.0, 10.0, 10.0, 10.0, 8.0, 6.0, 4.0};

    // Bullets per magazine: bigger as the fire rate speeds up
    public static final int[]    MAGAZINE_SIZES = {0, 10, 14, 18, 22, 28, 34, 42, 50};

    // Seconds to reload
    public static final double[] RELOAD_TIMES   = {0, 2.0, 1.9, 1.8, 1.6, 1.5, 1.4, 1.3, 1.2};
}