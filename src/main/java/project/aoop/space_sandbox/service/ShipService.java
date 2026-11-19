package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.config.PhysicsConfig;
import project.aoop.space_sandbox.entity.CelestialBody;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.physics.GravitySystem;

import java.util.List;

@Service
public class ShipService {

    private final Ship          ship;
    private final GravitySystem gravitySystem;
    private final LevelService  levelService;
    private final BoostService  boostService;

    public ShipService(Ship ship, GravitySystem gravitySystem,
                       LevelService levelService, BoostService boostService) {
        this.ship          = ship;
        this.gravitySystem = gravitySystem;
        this.levelService  = levelService;
        this.boostService  = boostService;
    }

    /** The mouse decides where the ship faces: it swings toward targetAngle. */
    public void aimAt(double targetAngle, double dt) {
        if (PhysicsConfig.TURN_RATE <= 0) {                 // instant
            ship.setAngle(targetAngle);
            return;
        }
        double diff = targetAngle - ship.getAngle();
        diff = Math.atan2(Math.sin(diff), Math.cos(diff));  // the short way round
        double step = PhysicsConfig.TURN_RATE * dt;
        ship.setAngle(ship.getAngle() + Math.max(-step, Math.min(step, diff)));
    }

    public void drive(double dt, double forward, double strafe) {
        boostService.update(dt);

        double  limit    = levelService.getSpeedLimit();
        double  boostMax = levelService.getBoostSpeedLimit();
        boolean boosting = boostService.isActive();
        double  cap      = boosting ? boostMax : limit;
        double  before   = ship.getSpeed();

        boolean wantsMove = forward != 0 || strafe != 0;
        double  a         = ship.getAngle();

        // the direction the player wants to go in
        double dx, dy;
        if (PhysicsConfig.MOVE_RELATIVE_TO_SHIP) {
            double f = forward >= 0 ? forward : forward * PhysicsConfig.REVERSE_POWER;
            double s = strafe * PhysicsConfig.STRAFE_POWER;
            // front = (cos a, sin a), the ship's right-hand side = (-sin a, cos a)
            dx = f * Math.cos(a) - s * Math.sin(a);
            dy = f * Math.sin(a) + s * Math.cos(a);
        } else {
            dx = strafe;      // W = up the screen, D = right
            dy = -forward;
        }
        double len = Math.hypot(dx, dy);
        if (len > 1.0) { dx /= len; dy /= len; }      // diagonals aren't faster

        if (boosting && !wantsMove) {                 // boost with no keys pushes straight ahead
            dx = Math.cos(a);
            dy = Math.sin(a);
        }

        boolean accelerating = wantsMove || boosting;
        if (accelerating) {
            double accel = limit / PhysicsConfig.ACCEL_TIME
                    * (boosting ? PhysicsConfig.BOOST_ACCEL_MULT : 1.0);
            ship.setVx(ship.getVx() + dx * accel * dt);
            ship.setVy(ship.getVy() + dy * accel * dt);

            // thrust can never push the speed past the cap (or past what it already was, if over the cap)
            double ceiling = Math.max(cap, before);
            if (ship.getSpeed() > ceiling) ship.scaleSpeedTo(ceiling);
        }

        double speed = ship.getSpeed();
        if (speed > cap) {
            // the boost just ended: ease back down to the normal top speed
            double bleed = (boostMax - limit) / PhysicsConfig.BOOST_BLEED_TIME;
            ship.scaleSpeedTo(Math.max(cap, speed - bleed * dt));
        } else if (!accelerating) {
            // nothing held: coast down
            ship.scaleSpeedTo(Math.max(0, speed - limit / PhysicsConfig.COAST_TIME * dt));
        }
    }

    public void brake(double dt) {
        ship.brake(dt, levelService.getBrakePower());
    }

    public void update(List<CelestialBody> bodies, double dt) {
        gravitySystem.applyGravity(ship, bodies, dt);
        ship.update(dt);
    }

    public boolean isCrashSpeed() {
        return ship.getSpeed() > levelService.getSpeedLimit() * 3;
    }

    public void crash()   { ship.crash(); }
    public Ship getShip() { return ship; }
}