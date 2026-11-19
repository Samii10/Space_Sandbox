package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.Bullet;
import project.aoop.space_sandbox.entity.Impact;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.service.CombatService;

import java.util.List;

@Component
public class BulletRenderer {

    private final GameCamera    camera;
    private final CombatService combatService;

    public BulletRenderer(GameCamera camera, CombatService combatService) {
        this.camera = camera;
        this.combatService = combatService;
    }

    // Same signature as before, so GameRenderer needs no change
    public void render(GraphicsContext gc, List<Bullet> bullets) {
        double zoom = camera.getZoom();
        double unit = ShipGeometry.WORLD_PER_UNIT * zoom;   // same scale as the ship
        double t    = System.nanoTime() * 1e-9;

        // beams first (underneath), then bullets, then hit effects on top
        for (Bullet b : bullets) {
            if (!b.isLaser()) continue;
            drawBeam(gc, camera.toScreenX(b.getX()), camera.toScreenY(b.getY()), b, zoom, t);
        }
        for (Bullet b : bullets) {
            if (b.isLaser()) continue;
            double sx = camera.toScreenX(b.getX());
            double sy = camera.toScreenY(b.getY());
            if (sx < -80 || sx > camera.getScreenW() + 80) continue;
            if (sy < -80 || sy > camera.getScreenH() + 80) continue;
            drawPlasma(gc, sx, sy, b, unit);
        }
        for (Impact im : combatService.getImpacts()) {
            drawImpact(gc, im, unit);
        }
    }

    // ── BULLET: glowing plasma slug with a fading tail ───────────────────

    private static Color pc(boolean enemy, double r, double g, double b, double a) {
        return enemy ? Color.color(r, g * 0.2, b * 0.2, a) : Color.color(r, g, b, a);
    }

    private void drawPlasma(GraphicsContext gc, double sx, double sy, Bullet b, double unit) {
        boolean en  = b.isEnemy();
        double fade = Math.min(1.0, b.getLifetime() * 6.0);
        double len  = Math.max(unit * 3.4, 12);
        double hw   = Math.max(unit * 0.30, 1.7);
        double ang  = Math.toDegrees(b.getAngle());

        gc.save();
        gc.translate(sx, sy);
        gc.rotate(ang);

        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, pc(en, 1.0, 0.40, 0.05, 0.0)),
                new Stop(1.0, pc(en, 1.0, 0.62, 0.12, 0.55 * fade))));
        gc.fillPolygon(
                new double[]{-len * 1.6, -len * 0.2, 0, 0, -len * 0.2},
                new double[]{0, -hw * 1.5, -hw * 1.5, hw * 1.5, hw * 1.5}, 5);

        double hr = hw * 4.2;
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, pc(en, 1.0, 0.75, 0.25, 0.55 * fade)),
                new Stop(1.0, pc(en, 1.0, 0.40, 0.0, 0.0))));
        gc.fillOval(-hr, -hr, hr * 2, hr * 2);

        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, pc(en, 1.0, 0.85, 0.30, fade)),
                new Stop(0.5, pc(en, 1.0, 0.60, 0.12, fade)),
                new Stop(1.0, pc(en, 1.0, 0.85, 0.30, fade))));
        gc.fillRoundRect(-len * 0.5, -hw, len * 0.5 + hw, hw * 2, hw * 2, hw * 2);

        gc.setFill(pc(en, 1.0, 0.97, 0.82, fade));
        gc.fillRoundRect(-len * 0.32, -hw * 0.45, len * 0.32 + hw * 0.7, hw * 0.9, hw * 0.9, hw * 0.9);

        gc.restore();
    }

    // ── BULLET HIT: flash + shock ring + sparks spraying back ────────────

    private void drawImpact(GraphicsContext gc, Impact im, double unit) {
        double sx = camera.toScreenX(im.getX());
        double sy = camera.toScreenY(im.getY());
        if (sx < -60 || sx > camera.getScreenW() + 60) return;
        if (sy < -60 || sy > camera.getScreenH() + 60) return;

        double p    = im.getProgress();
        double fade = 1.0 - p;
        double u    = Math.max(unit, 1.2);

        double fr = u * (1.4 + 2.6 * p) + 3;
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1.0, 0.97, 0.80, 0.9 * fade)),
                new Stop(0.4, Color.color(1.0, 0.65, 0.20, 0.6 * fade)),
                new Stop(1.0, Color.color(1.0, 0.35, 0.0, 0.0))));
        gc.fillOval(sx - fr, sy - fr, fr * 2, fr * 2);

        double rr = u * (1.0 + 4.0 * p) + 2;
        gc.setStroke(Color.color(1.0, 0.7, 0.3, 0.6 * fade));
        gc.setLineWidth(1.5);
        gc.strokeOval(sx - rr, sy - rr, rr * 2, rr * 2);

        double back = im.getAngle() + Math.PI;
        for (int i = 0; i < 6; i++) {
            double a = back + (i - 2.5) * 0.45;
            double d = u * (1.0 + 5.0 * p) * (0.6 + 0.4 * ((i * 37) % 10) / 10.0);
            double s = Math.max(1.2, u * 0.25 * fade);
            gc.setFill(Color.color(1.0, 0.85, 0.4, fade));
            gc.fillOval(sx + Math.cos(a) * d - s, sy + Math.sin(a) * d - s, s * 2, s * 2);
        }
    }

    // ── LASER: layered blue beam with a muzzle flare and travelling pulses ─

    private void drawBeam(GraphicsContext gc, double sx, double sy, Bullet b,
                          double zoom, double t) {
        double env = b.getEnvelope();
        if (env <= 0.01) return;

        double  length  = b.getBeamLength() * zoom;     // already cut short if blocked
        boolean blocked = b.isBeamBlocked();
        double  hw      = Math.max(b.getBeamHalfWidth() * zoom, 1.5) * (0.94 + 0.06 * Math.sin(t * 55));

        gc.save();
        gc.translate(sx, sy);
        gc.rotate(Math.toDegrees(b.getAngle()));

        if (length > 2) {
            boolean fade = !blocked;   // a blocked beam stays bright right up to the planet
            beamLayer(gc, length, hw * 5.0,  0.07 * env, 0.10, 0.45, 1.00, fade);
            beamLayer(gc, length, hw * 3.0,  0.16 * env, 0.12, 0.55, 1.00, fade);
            beamLayer(gc, length, hw * 1.8,  0.45 * env, 0.20, 0.70, 1.00, fade);
            beamLayer(gc, length, hw * 1.0,  0.85 * env, 0.45, 0.88, 1.00, fade);
            beamLayer(gc, length, hw * 0.45, 1.00 * env, 0.92, 0.98, 1.00, fade);

            // bright pulses racing along the beam
            int    pulses  = 7;
            double spacing = length / pulses;
            for (int i = 0; i < pulses; i++) {
                double px = (t * 900 + i * spacing) % length;
                double a  = Math.max(0, Math.min(1, 0.6 * env * (1.0 - px / length)));
                gc.setFill(Color.color(0.9, 0.98, 1.0, a));
                gc.fillRoundRect(px, -hw * 0.55, Math.max(hw * 5, 14), hw * 1.1, hw * 1.1, hw * 1.1);
            }
        }

        // muzzle flare
        double fr = hw * 4.5 + 8;
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.95, 1.0, 1.0, Math.min(1, 0.95 * env))),
                new Stop(0.3, Color.color(0.35, 0.80, 1.0, 0.6 * env)),
                new Stop(1.0, Color.color(0.10, 0.40, 1.0, 0.0))));
        gc.fillOval(-fr, -fr, fr * 2, fr * 2);

        if (blocked) drawBeamImpact(gc, length, hw, env, t);

        gc.restore();
    }

    // scorch flare + sparks where the beam meets a planet
    private void drawBeamImpact(GraphicsContext gc, double length, double hw, double env, double t) {
        double fr = hw * 4.0 + 10;
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.95, 1.0, 1.0, Math.min(1, 0.95 * env))),
                new Stop(0.35, Color.color(0.30, 0.75, 1.0, 0.7 * env)),
                new Stop(1.0, Color.color(0.10, 0.40, 1.0, 0.0))));
        gc.fillOval(length - fr, -fr, fr * 2, fr * 2);

        for (int i = 0; i < 8; i++) {
            double phase = (t * 7 + i * 0.137) % 1.0;
            double a = Math.PI + (i - 3.5) * 0.38;
            double d = (hw * 1.5 + 6) + phase * (hw * 6 + 26);
            double s = Math.max(1.2, hw * 0.35 * (1 - phase));
            gc.setFill(Color.color(0.75, 0.95, 1.0, Math.max(0, Math.min(1, (1 - phase) * env))));
            gc.fillOval(length + Math.cos(a) * d - s, Math.sin(a) * d - s, s * 2, s * 2);
        }
    }

    // one rounded layer of the beam
    private void beamLayer(GraphicsContext gc, double length, double width,
                           double alpha, double r, double g, double bl, boolean fadeEnd) {
        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.color(r, g, bl, alpha)),
                new Stop(0.75, Color.color(r, g, bl, alpha * 0.85)),
                new Stop(1.00, Color.color(r, g, bl, fadeEnd ? 0 : alpha * 0.9))));
        gc.fillRoundRect(0, -width / 2, length, width, width, width);
    }
}