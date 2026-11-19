package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.config.PhysicsConfig;

/** The boost tank: hold Shift to drain it; release it to stop and refuel. */
@Service
public class BoostService {

    public static final double MIN_TO_START = 0.25;   // need at least a quarter tank to start
    private double  fuel     = 1.0;    // 0..1
    private boolean active   = false;
    private boolean held = false;

    /** Set whether Shift is currently held. */
    public void setHeld(boolean held) {
        this.held = held;
        if (held) {
            if (!active && fuel >= MIN_TO_START) active = true;
        } else {
            active = false;
        }
    }

    /** Kept for compatibility; held-input boosting uses setHeld(...). */
    public void toggle() {
        if (active) {
            active = false;
            held = false;
        } else if (fuel >= MIN_TO_START) {
            active = true;
            held = true;
        }
    }

    /** Called every frame. */
    public void update(double dt) {
        if (held && !active && fuel >= MIN_TO_START) active = true;

        if (active) {
            fuel = Math.max(0, fuel - dt / PhysicsConfig.BOOST_DURATION);
            if (fuel <= 0) active = false;
        } else {
            if (fuel < 1.0) {
                fuel = Math.min(1.0, fuel + dt / PhysicsConfig.BOOST_REFUEL_TIME);
            }
        }
    }

    public void reset() {
        fuel = 1.0;
        active = false;
        held = false;
    }

    public boolean isActive() { return active; }
    public boolean isReady()  { return !active && fuel >= MIN_TO_START; }
    public double  getFuel()  { return fuel; }
}