package project.aoop.space_sandbox.entity;

import org.springframework.stereotype.Component;

/** The partner's ship: a "ghost" Ship driven by network snapshots, smoothed so it glides. */
@Component
public class RemoteShip {

    private final Ship ghost = new Ship();   // a plain object, NOT the player's Ship bean

    private boolean connected = false;
    private boolean hasData   = false;
    private boolean thrusting = false;

    private double tx, ty, tvx, tvy, tAngle;   // latest position the partner reported
    private long   lastUpdateNanos;

    public void applySnapshot(double x, double y, double vx, double vy,
                              double angle, int health, boolean thrusting) {
        tx = x;
        ty = y;
        tvx = vx;
        tvy = vy;
        tAngle = angle;
        this.thrusting = thrusting;
        ghost.setHealth(health);
        lastUpdateNanos = System.nanoTime();

        if (!hasData) {                        // first packet: appear exactly there
            ghost.setX(x);
            ghost.setY(y);
            ghost.setAngle(angle);
            hasData = true;
        }
    }

    /** Called every frame: slide toward where the partner should be by now. */
    public void interpolate(double dt) {
        if (!isVisible()) return;

        double age = Math.min(0.25, (System.nanoTime() - lastUpdateNanos) * 1e-9);
        double px = tx + tvx * age;            // dead reckoning: last position + velocity
        double py = ty + tvy * age;
        double dx = px - ghost.getX();
        double dy = py - ghost.getY();

        if (dx * dx + dy * dy > 50.0 * 50.0) { // far away (teleport): just snap
            ghost.setX(px);
            ghost.setY(py);
            ghost.setAngle(tAngle);
        } else {
            double k = Math.min(1.0, 18.0 * dt);
            ghost.setX(ghost.getX() + dx * k);
            ghost.setY(ghost.getY() + dy * k);
            double da = tAngle - ghost.getAngle();
            da = Math.atan2(Math.sin(da), Math.cos(da));     // shortest way round
            ghost.setAngle(ghost.getAngle() + da * k);
        }
        ghost.setVx(tvx);
        ghost.setVy(tvy);
    }

    public void setConnected(boolean c) {
        connected = c;
        if (!c) hasData = false;
    }

    public boolean isVisible()   { return connected && hasData; }
    public boolean isThrusting() { return thrusting; }
    public Ship    getGhost()    { return ghost; }
}