package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.config.PhysicsConfig;

import java.util.*;

@Service
public class LevelService {

    public static final int MAX_LEVEL   = 8;   // level 8 = boss battle at Earth
    public static final int LASER_LEVEL = 4;   // Jupiter
    private int level = 1;
    private final Set<String> visitedPlanets = new HashSet<>();

    // Planet order: Earth > Venus > Mercury > Mars > Jupiter > Saturn > Uranus > Neptune
    // Safe planets: Earth, Mercury, Saturn
    // Enemy planets: Venus, Mars, Jupiter, Uranus, Neptune
    public int getPlanetLevel(String name) {
        return switch (name) {
            case "Earth"   -> level >= MAX_LEVEL ? MAX_LEVEL : 0;
            case "Moon"    -> 0;
            case "Venus"   -> 1;
            case "Mercury" -> 2;
            case "Mars"    -> 3;
            case "Jupiter" -> 4;
            case "Saturn"  -> 5;
            case "Uranus"  -> 6;
            case "Neptune" -> 7;
            default        -> 0;
        };
    }

    // Safe = no enemies, just land and collect
    public boolean isSafePlanet(String name) {
        return switch (name) {
            case "Earth"          -> level < MAX_LEVEL;   // boss arena on the last level
            case "Moon", "Mercury" -> true;
            default               -> false;
        };
    }

    public boolean isBossLevel() { return level >= MAX_LEVEL; }

    // ── Planet access ─────────────────────────────────────────────────────
    public boolean isGravityActive(String name) { return getPlanetLevel(name) == level; }
    public boolean isAccessible(String name)    { return getPlanetLevel(name) <= level; }
    public boolean isMastered(String name)      { return getPlanetLevel(name) < level;  }
    public boolean isLocked(String name)        { return getPlanetLevel(name) > level;  }

    // ── Level-based ship stats ─────────────────────────────────────────────
    public double getSpeedLimit()        { return PhysicsConfig.SPEED_LIMITS[level];    }
    public double getThrustPower()       { return PhysicsConfig.THRUST_POWERS[level];   }
    public double getBrakePower()        { return getSpeedLimit() / PhysicsConfig.BRAKE_STOP_TIMES[level]; }
    public double getFireRateCooldown()  { return PhysicsConfig.FIRE_RATES[level];      }
    public double getLaserChargeTime()   { return PhysicsConfig.CHARGE_TIMES[level];    }
    public int    getMagazineSize()      { return PhysicsConfig.MAGAZINE_SIZES[level];  }
    public double getReloadTime()        { return PhysicsConfig.RELOAD_TIMES[level];    }

    public double getDamageMultiplier()  { return 1.0 + 0.35 * (level - 1); }
    public double getArmorMultiplier()   { return 1.0 / (1.0 + 0.12 * (level - 1)); }

    public List<String> getAccessiblePlanets() {
        List<String> list = new ArrayList<>(Arrays.asList("Earth", "Moon"));
        if (level >= 1) list.add("Venus");
        if (level >= 2) list.add("Mercury");
        if (level >= 3) list.add("Mars");
        if (level >= 4) list.add("Jupiter");
        if (level >= 5) list.add("Saturn");
        if (level >= 6) list.add("Uranus");
        if (level >= 7) list.add("Neptune");
        return list;
    }

    public String getTargetPlanet() {
        return switch (level) {
            case 1 -> "Venus";
            case 2 -> "Mercury";
            case 3 -> "Mars";
            case 4 -> "Jupiter";
            case 5 -> "Saturn";
            case 6 -> "Uranus";
            case 7 -> "Neptune";
            default -> "Earth";   // level 8: back home for the boss
        };
    }

    public void visitPlanet(String name) {
        if (visitedPlanets.contains(name)) return;
        visitedPlanets.add(name);
        System.out.println("✓ Visited: " + name);
    }

    private String describe() {
        String target = getTargetPlanet();
        if (level >= MAX_LEVEL) return "BOSS BATTLE";
        return isSafePlanet(target) ? "SAFE" : "ENEMIES";
    }

    public void levelUp() {
        if (level < 8) {
            level++;
            System.out.println("★ LEVEL " + level
                    + " — target: " + getTargetPlanet()
                    + " | speed: " + getSpeedLimit() + " u/s"
                    + " | " + describe());
        }
    }

    public void levelDown() {
        if (level > 1) {
            level--;
            System.out.println("★ LEVEL " + level
                    + " — target: " + getTargetPlanet()
                    + " | speed: " + getSpeedLimit() + " u/s");
        }
    }

    // Boost speed = the level's top speed x BOOST_FACTOR (level 1: 8 -> 12 u/s)
    public double getBoostSpeedLimit() {
        return getSpeedLimit() * PhysicsConfig.BOOST_FACTOR;
    }

    // Laser unlocked at level 5 (Jupiter)
    public boolean hasLaser()        { return level >= 5; }
    public int     getLevel()        { return level; }
    public Set<String> getVisited()  { return visitedPlanets; }
}