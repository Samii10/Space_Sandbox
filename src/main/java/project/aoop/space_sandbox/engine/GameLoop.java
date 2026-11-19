package project.aoop.space_sandbox.engine;

import javafx.animation.AnimationTimer;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.CelestialBody;
import project.aoop.space_sandbox.entity.Moon;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.entity.Star;
import project.aoop.space_sandbox.input.InputHandler;
import project.aoop.space_sandbox.network.NetworkService;
import project.aoop.space_sandbox.physics.OrbitalMechanics;
import project.aoop.space_sandbox.render.GameRenderer;
import project.aoop.space_sandbox.render.MinimapRenderer;
import project.aoop.space_sandbox.render.SolarSystemMap;
import project.aoop.space_sandbox.render.TempMenu;
import project.aoop.space_sandbox.service.AsteroidService;
import project.aoop.space_sandbox.service.BoostService;
import project.aoop.space_sandbox.service.CombatService;
import project.aoop.space_sandbox.service.EnemyService;
import project.aoop.space_sandbox.service.GameSession;
import project.aoop.space_sandbox.service.LevelService;
import project.aoop.space_sandbox.service.ShipService;

import java.util.ArrayList;
import java.util.List;

@Component
public class GameLoop extends AnimationTimer {

    private static final double AIM_DEAD_ZONE = 14.0;   // pixels around the ship where the cursor is ignored

    private long    lastTime      = 0;
    private boolean levelUpHeld   = false;
    private boolean levelDownHeld = false;
    private boolean escHeld       = false;
    private boolean leaveHeld     = false;
    private boolean mapKeyHeld    = false;
    private boolean reloadHeld = false;

    // Set by JavaFXLauncher after the canvas is created
    private double canvasW = 1280;
    private double canvasH = 720;

    private final GameState        gameState;
    private final ShipService      shipService;
    private final InputHandler     inputHandler;
    private final GameRenderer     renderer;
    private final GameCamera       camera;
    private final Star             star;
    private final List<Planet>     planets;
    private final OrbitalMechanics orbitalMechanics;
    private final LevelService     levelService;
    private final CombatService    combatService;
    private final AsteroidService  asteroidService;
    private final BoostService     boostService;
    private final SolarSystemMap   solarSystemMap;
    private final MinimapRenderer  minimapRenderer;
    private final NetworkService   networkService;
    private final GameSession      gameSession;
    private final EnemyService     enemyService;
    private final TempMenu         tempMenu;

    public GameLoop(GameState gameState, ShipService shipService,
                    InputHandler inputHandler, GameRenderer renderer,
                    GameCamera camera, Star star, List<Planet> planets,
                    OrbitalMechanics orbitalMechanics, LevelService levelService,
                    CombatService combatService, AsteroidService asteroidService,
                    EnemyService enemyService, BoostService boostService,
                    SolarSystemMap solarSystemMap, MinimapRenderer minimapRenderer,
                    NetworkService networkService, GameSession gameSession,
                    TempMenu tempMenu) {
        this.gameState        = gameState;
        this.shipService      = shipService;
        this.inputHandler     = inputHandler;
        this.renderer         = renderer;
        this.camera           = camera;
        this.star             = star;
        this.planets          = planets;
        this.orbitalMechanics = orbitalMechanics;
        this.levelService     = levelService;
        this.combatService    = combatService;
        this.asteroidService  = asteroidService;
        this.boostService     = boostService;
        this.solarSystemMap   = solarSystemMap;
        this.minimapRenderer  = minimapRenderer;
        this.networkService   = networkService;
        this.gameSession      = gameSession;
        this.enemyService     = enemyService;
        this.tempMenu         = tempMenu;
    }

    public void setCanvasSize(double w, double h) {
        this.canvasW = w;
        this.canvasH = h;
    }

    @Override
    public void handle(long now) {
        if (lastTime == 0) { lastTime = now; return; }
        double dt = Math.min((now - lastTime) / 1_000_000_000.0, 0.05);
        lastTime = now;

        // ── Menu: nothing of the game runs ────────────────────────────────
        if (gameState.getState() == GameState.State.MENU) {
            tempMenu.update(canvasW, canvasH);
            renderer.renderMenu();
            inputHandler.clearFrameState();
            return;
        }

        // K = back to the menu (this also leaves a co-op session)
        if (inputHandler.leaveCoop()) {
            if (!leaveHeld) {
                leaveHeld = true;
                gameSession.leaveToMenu();
                tempMenu.reset();
                inputHandler.clearFrameState();
                return;
            }
        } else { leaveHeld = false; }

        if (!gameState.isRunning()) return;   // destroyed: the frame stays frozen

        Ship ship = shipService.getShip();

        // ── 1. Input ──────────────────────────────────────────────────────

        // Shift is held to boost. Releasing it stops the boost and immediately refuels.
        boostService.setHeld(inputHandler.boostKey());

        // M = open / close the map (clicking the minimap still works too)
        if (inputHandler.mapKey()) {
            if (!mapKeyHeld) { solarSystemMap.toggle(); mapKeyHeld = true; }
        } else { mapKeyHeld = false; }

        double  mx          = inputHandler.getMouseX();
        double  my          = inputHandler.getMouseY();
        boolean overMinimap = minimapRenderer.isClickOnMinimap(mx, my, canvasW, canvasH);
        boolean mapOpen     = solarSystemMap.isOpen();

        // The MOUSE aims the ship. Not while the map is open or the cursor is on the minimap.
        if (inputHandler.hasMouse() && !mapOpen && !overMinimap) {
            double sx = camera.toScreenX(ship.getX());
            double sy = camera.toScreenY(ship.getY());
            double dx = mx - sx;
            double dy = my - sy;
            if (dx * dx + dy * dy > AIM_DEAD_ZONE * AIM_DEAD_ZONE) {
                shipService.aimAt(Math.atan2(dy, dx), dt);
            }
        }

        // W / A / D (and S) move the ship relative to the way it faces.
        // Called every frame, so the ship slows down by itself when no key is held.
        shipService.drive(dt, inputHandler.forwardAxis(), inputHandler.strafeAxis());
        boolean engineOn = inputHandler.forwardAxis() > 0 || boostService.isActive();

        if (inputHandler.brake()) shipService.brake(dt);

        // Weapons: clicks on the minimap / open map must never fire them
        if (inputHandler.isMouseJustPressed() && !overMinimap && !mapOpen) {
            combatService.onMousePressed(ship);
        }
        if (inputHandler.isMouseHeld() && !overMinimap && !mapOpen) {
            combatService.onMouseHeld(ship, dt);
        }
        if (inputHandler.isMouseJustReleased()) {
            if (mapOpen) {
                renderer.handleClick(mx, my);          // click a planet to track it
            } else if (overMinimap && !combatService.isCharging()) {
                solarSystemMap.open();                 // click the minimap to open the map
            } else {
                combatService.onMouseReleased(ship);
            }
        }

        // Right click: switch bullet <-> laser
        if (inputHandler.isSecondaryMouseJustPressed()) {
            combatService.toggleWeaponMode();
        }

        // R = reload (the magazine also reloads by itself when it runs empty)
        if (inputHandler.reloadKey()) {
            if (!reloadHeld) { combatService.reload(); reloadHeld = true; }
        } else { reloadHeld = false; }

        // L = level up, P = level down. In co-op only the host decides the shared level.
        if (!networkService.isClient()) {
            if (inputHandler.levelUp()) {
                if (!levelUpHeld) { levelService.levelUp(); levelUpHeld = true; }
            } else { levelUpHeld = false; }

            if (inputHandler.levelDown()) {
                if (!levelDownHeld) { levelService.levelDown(); levelDownHeld = true; }
            } else { levelDownHeld = false; }
        }

        if (inputHandler.closeMap()) {
            if (!escHeld) { solarSystemMap.close(); escHeld = true; }
        } else { escHeld = false; }

        // ── 2. Move planets ───────────────────────────────────────────────
        for (Planet p : planets) {
            if (p instanceof Moon m) {
                m.updateAroundEarth(dt);
            } else {
                orbitalMechanics.update(p, dt);
            }
        }

        // ── 3. Ship physics ───────────────────────────────────────────────
        List<CelestialBody> bodies = new ArrayList<>();
        bodies.add(star);
        bodies.addAll(planets);
        shipService.update(bodies, dt);

        // ── 4. Co-op: apply what the partner sent, publish where we are ───
        networkService.update(dt, ship, engineOn);

        // ── 5. Weapons, then asteroids (both AFTER the ships moved) ───────
        combatService.update(dt, ship);
        enemyService.update(dt, ship);
        asteroidService.update(dt, ship);

        if (ship.getHealth() <= 0) {
            destroyShip(ship, enemyService.killedPlayer()
                    ? "💥 DESTROYED — shot down by an enemy ship"
                    : "☄ DESTROYED — hull breached by asteroids");
            return;
        }

        // ── 6. Sun and planet collisions (ship radius included) ───────────
        double shipR = ShipGeometry.COLLISION_RADIUS;

        if (dist(ship, star.getX(), star.getY()) <= star.getRadius() + shipR) {
            destroyShip(ship, "☀ DESTROYED — vaporized by the Sun");
            return;
        }

        for (Planet p : planets) {
            double dx = ship.getX() - p.getX();
            double dy = ship.getY() - p.getY();
            double d  = Math.sqrt(dx * dx + dy * dy);
            double contact = p.getRadius() + shipR;
            if (d > contact) continue;

            if (levelService.isLocked(p.getName())) {
                destroyShip(ship, "💀 DESTROYED — pulled into " + p.getName()
                        + " (locked — reach level "
                        + levelService.getPlanetLevel(p.getName()) + " first)");
                return;
            }
        }

        // ── 7. Camera locks onto the ship ─────────────────────────────────
        camera.follow(ship.getX(), ship.getY(), dt);

        // ── 8. Render ─────────────────────────────────────────────────────
        renderer.render(ship, engineOn);

        // ── 9. Clear one-frame input flags ────────────────────────────────
        inputHandler.clearFrameState();
    }

    // One place for every death: tell the partner, pause, draw a last frame with the overlay
    private void destroyShip(Ship ship, String message) {
        ship.crash();
        networkService.publishLocal(ship, false);
        gameState.setState(GameState.State.PAUSED);
        System.out.println(message);
        renderer.render(ship, false);
    }

    private double dist(Ship ship, double tx, double ty) {
        double dx = ship.getX() - tx;
        double dy = ship.getY() - ty;
        return Math.sqrt(dx * dx + dy * dy);
    }
}