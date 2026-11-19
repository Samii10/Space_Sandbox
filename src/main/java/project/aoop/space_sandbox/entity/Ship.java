package project.aoop.space_sandbox.entity;

import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.config.PhysicsConfig;

@Component
public class Ship {

    private double x          = PhysicsConfig.SHIP_START_X;
    private double y          = PhysicsConfig.SHIP_START_Y;
    private double vx         = PhysicsConfig.SHIP_START_VX;
    private double vy         = PhysicsConfig.SHIP_START_VY;
    private double angle      = 0;
    private double radius     = 0.3;
    private int    engineTier = 1;
    private int    health     = 100;

    public void thrust(double deltaTime, double power) {
        vx += Math.cos(angle) * power * deltaTime;
        vy += Math.sin(angle) * power * deltaTime;
    }

    public void rotateLeft(double deltaTime)  { angle -= PhysicsConfig.SHIP_ROTATE_SPEED * deltaTime; }
    public void rotateRight(double deltaTime) { angle += PhysicsConfig.SHIP_ROTATE_SPEED * deltaTime; }

    public void brake(double deltaTime, double brakePower) {
        double speed = getSpeed();
        if (speed == 0) return;

        double reducedSpeed = Math.max(0, speed - brakePower * deltaTime);
        double scale = reducedSpeed / speed;
        vx *= scale;
        vy *= scale;
    }

    public void update(double deltaTime) {
        x += vx * deltaTime;
        y += vy * deltaTime;
    }

    public void crash()      { health = 0; }
    public double getSpeed() { return Math.sqrt(vx * vx + vy * vy); }

    public double getX()       { return x; }
    public double getY()       { return y; }
    public double getVx()      { return vx; }
    public double getVy()      { return vy; }
    public double getAngle()   { return angle; }
    public double getRadius()  { return radius; }
    public int getEngineTier() { return engineTier; }
    public int getHealth()     { return health; }

    public void setX(double x)          { this.x = x; }
    public void setY(double y)          { this.y = y; }
    public void setVx(double vx)        { this.vx = vx; }
    public void setVy(double vy)        { this.vy = vy; }
    public void setAngle(double angle)  { this.angle = angle; }
    public void setHealth(int h)        { this.health = h; }
    public void setEngineTier(int tier) { this.engineTier = tier; }

    /** Adds speed along the direction the ship is facing. */
    public void thrustBy(double accel, double deltaTime) {
        vx += Math.cos(angle) * accel * deltaTime;
        vy += Math.sin(angle) * accel * deltaTime;
    }

    /** Changes the speed without changing the direction of travel. */
    public void scaleSpeedTo(double newSpeed) {
        double s = getSpeed();
        if (s < 1e-9) return;
        double k = Math.max(0, newSpeed) / s;
        vx *= k;
        vy *= k;
    }

    public void reset() {
        x = PhysicsConfig.SHIP_START_X;
        y = PhysicsConfig.SHIP_START_Y;
        vx = PhysicsConfig.SHIP_START_VX;
        vy = PhysicsConfig.SHIP_START_VY;
        angle = 0;
        health = 100;
    }
}