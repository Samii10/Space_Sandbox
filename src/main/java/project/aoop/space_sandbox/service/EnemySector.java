package project.aoop.space_sandbox.service;

import project.aoop.space_sandbox.entity.EnemyKind;

/**
 * Everything that makes one planet's fight different from another's.
 * Tune the whole game's difficulty curve in forLevel() below.
 *
 * @param laserGrunts    how many grunts carry a laser (-1 = all of them)
 * @param accuracy       0..1: how well plasma shots lead the target (and how tight the spread is)
 * @param accurateOpener first shot of every engagement is perfectly aimed
 * @param laserCharge    seconds to charge a grunt's laser (bosses charge faster, see EnemyKind)
 * @param beamTrack      rad/s the ship can turn while its beam is on (low = easy to dodge)
 * @param laserDps       beam damage per second to the player (bosses multiply this)
 * @param speedMult      multiplier on every ship's top speed
 * @param boss           boss tier to add, or null
 * @param shareAlert     when one ship spots you / gets hit, the whole squad hunts you
 * @param surround       grunts take slots all around you instead of charging in a line (flanking)
 * @param persistent     huge aggro range, no leash, catches up when you try to run
 */
public record EnemySector(
        String    planet,
        int       grunts,
        int       laserGrunts,
        double    accuracy,
        boolean   accurateOpener,
        double    laserCharge,
        double    beamTrack,
        double    laserDps,
        double    speedMult,
        EnemyKind boss,
        boolean   shareAlert,
        boolean   surround,
        boolean   persistent) {

    public int total() { return grunts + (boss != null ? 1 : 0); }

    /** The sector for a level, or null when the level has no enemies (Mercury). */
    public static EnemySector forLevel(int level) {
        return switch (level) {
            //                         planet     grunts las  acc   opener charge track dps  speed boss                 alert  surr   persist
            case 1 -> new EnemySector("Venus",     5,   0, 0.10, false, 0.0, 0.00,  0.0, 0.8, null,                 false, false, false);
            case 3 -> new EnemySector("Mars",      8,   1, 0.35, false, 5.0, 0.35, 10.0, 0.9, null,                 false, false, false);
            case 4 -> new EnemySector("Jupiter",  12,  -1, 0.50, true,  5.0, 0.50, 12.0, 1.0, EnemyKind.BOSS_LOW,   true,  false, false);
            case 5 -> new EnemySector("Saturn",   15,  -1, 0.60, true,  4.5, 0.65, 14.0, 1.1, null,                 true,  false, false);
            case 6 -> new EnemySector("Uranus",   18,  -1, 0.70, true,  4.0, 0.80, 16.0, 1.2, null,                 true,  false, false);
            case 7 -> new EnemySector("Neptune",  20,  -1, 0.80, true,  3.5, 0.90, 18.0, 1.3, EnemyKind.BOSS_MID,   true,  true,  true);
            case 8 -> new EnemySector("Earth",     0,   0, 0.85, true,  5.0, 0.80, 16.0, 1.0, EnemyKind.BOSS_FINAL, true,  false, true);
            default -> null;   // level 2 = Mercury, safe
        };
    }
}