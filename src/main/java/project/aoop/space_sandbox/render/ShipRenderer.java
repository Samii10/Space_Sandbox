package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.service.CombatService;

/**
 * Smooth vector ship, drawn in "design units" (see ShipGeometry).
 * The nose points to -y in design space; the +90 rotation below turns
 * that into the ship's heading (angle 0 = facing right).
 */
@Component
public class ShipRenderer {

    private static final Color OUTLINE   = Color.web("#0b0c1d");
    private static final Color ORANGE    = Color.web("#ff8a1f");
    private static final Color CYAN      = Color.web("#33e0f0");
    private static final Color CYAN_DEEP = Color.web("#0b8fa3");

    private static final double ENGINE_X = 3.4;
    private static final double NOZZLE_Y = 6.0;

    private final CombatService combatService;

    public ShipRenderer(CombatService combatService) {
        this.combatService = combatService;
    }

    // Same signature as before, so GameRenderer needs no change
    public void render(GraphicsContext gc, GameCamera cam, Ship ship, boolean thrusting) {
        renderInternal(gc, cam, ship, thrusting, true);
    }

    public void renderRemote(GraphicsContext gc, GameCamera cam, Ship ship, boolean thrusting) {
        renderInternal(gc, cam, ship, thrusting, false);
    }

    private void renderInternal(GraphicsContext gc, GameCamera cam, Ship ship,
                                boolean thrusting, boolean localEffects) {
        // Draw at the ship's TRUE screen position so it always lines up with the shots
        double sx   = cam.toScreenX(ship.getX());
        double sy   = cam.toScreenY(ship.getY());
        double unit = ShipGeometry.WORLD_PER_UNIT * cam.getZoom();   // pixels per design unit
        double t    = System.nanoTime() * 1e-9;

        if (localEffects && combatService.isCharging()) {
            drawChargeRing(gc, sx, sy, unit, combatService.getCharge(),
                    combatService.isFullyCharged(), t);
        }

        gc.save();
        gc.translate(sx, sy);
        gc.rotate(Math.toDegrees(ship.getAngle()) + 90.0);   // nose (-y) -> heading
        gc.scale(unit, unit);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setLineCap(StrokeLineCap.ROUND);

        drawFlames(gc, thrusting, t);
        drawWings(gc);
        drawBulletBarrel(gc, localEffects ? combatService.getBulletFlash() : 0);
        drawLaserEmitter(gc, localEffects ? combatService.getCharge() : 0, false);
        drawHull(gc);
        drawEngines(gc, thrusting);
        drawRearBlock(gc);
        drawCockpit(gc);

        gc.restore();
    }

    // ── gradient helpers (proportional = relative to the shape's own bounds) ──

    private static LinearGradient lin(double x0, double y0, double x1, double y1, Stop... s) {
        return new LinearGradient(x0, y0, x1, y1, true, CycleMethod.NO_CYCLE, s);
    }

    private static RadialGradient rad(double focusAngle, double focusDist, Stop... s) {
        return new RadialGradient(focusAngle, focusDist, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE, s);
    }

    private static Stop st(double offset, Color c) { return new Stop(offset, c); }

    private static Color rgba(double r, double g, double b, double a) {
        return Color.color(clamp(r), clamp(g), clamp(b), clamp(a));
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private static double lerp(double a, double b, double f) { return a + (b - a) * f; }

    // ── 1. ENGINE FLAMES ─────────────────────────────────────────────────

    private void drawFlames(GraphicsContext gc, boolean thrusting, double t) {
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * ENGINE_X;

            if (!thrusting) {
                // idle pilot light
                gc.setFill(rad(0, 0, st(0, rgba(0.2, 0.9, 1, 0.35)), st(1, rgba(0.2, 0.9, 1, 0))));
                gc.fillOval(cx - 1.0, NOZZLE_Y - 0.4, 2.0, 2.0);
                continue;
            }

            double len = 3.8 + 0.6 * Math.sin(t * 38 + s) + 0.4 * Math.sin(t * 61 + 2 * s);

            // heat halo
            gc.setFill(rad(0, 0, st(0, rgba(1, 0.65, 0.2, 0.32)), st(1, rgba(1, 0.4, 0, 0))));
            gc.fillOval(cx - 2.4, NOZZLE_Y - 1.2, 4.8, len + 2.4);

            // outer flame
            flame(gc, cx, 1.0, len, lin(0, 0, 0, 1,
                    st(0.00, Color.web("#8ff6ff")),
                    st(0.12, Color.web("#fff6b0")),
                    st(0.45, Color.web("#ffb02e")),
                    st(0.80, rgba(1, 0.35, 0.05, 0.55)),
                    st(1.00, rgba(1, 0.2, 0, 0))));

            // white-hot core
            flame(gc, cx, 0.5, len * 0.55, lin(0, 0, 0, 1,
                    st(0.0, Color.WHITE),
                    st(0.6, Color.web("#fff3a0")),
                    st(1.0, rgba(1, 0.9, 0.4, 0))));
        }
    }

    private void flame(GraphicsContext gc, double cx, double halfW, double len, Paint paint) {
        gc.setFill(paint);
        gc.beginPath();
        gc.moveTo(cx - halfW, NOZZLE_Y);
        gc.quadraticCurveTo(cx - halfW * 1.15, NOZZLE_Y + len * 0.55, cx, NOZZLE_Y + len);
        gc.quadraticCurveTo(cx + halfW * 1.15, NOZZLE_Y + len * 0.55, cx + halfW, NOZZLE_Y);
        gc.closePath();
        gc.fill();
    }

    // ── 2. WINGS ─────────────────────────────────────────────────────────

    private void wingPath(GraphicsContext gc, double s) {
        gc.beginPath();
        gc.moveTo(s * 2.4, -2.1);                                // root, front
        gc.quadraticCurveTo(s * 6.0, -1.7, s * 9.0, -0.4);       // leading edge
        gc.quadraticCurveTo(s * 9.9, 0.0, s * 9.6, 0.9);         // rounded tip
        gc.lineTo(s * 8.6, 2.3);
        gc.quadraticCurveTo(s * 6.4, 3.3, s * 4.2, 4.0);         // trailing edge
        gc.lineTo(s * 2.4, 3.8);                                 // root, rear
        gc.closePath();
    }

    private void drawWings(GraphicsContext gc) {
        for (int s = -1; s <= 1; s += 2) {
            wingPath(gc, s);
            Stop a = st(0.00, Color.web("#3b4388"));
            Stop b = st(0.55, Color.web("#2a3068"));
            Stop c = st(1.00, Color.web("#1a1f4c"));
            gc.setFill(s > 0 ? lin(0, 0, 1, 0, a, b, c) : lin(1, 0, 0, 0, a, b, c));
            gc.fill();
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.16);
            gc.stroke();

            // inset panel
            gc.beginPath();
            gc.moveTo(s * 3.4, -0.9);
            gc.quadraticCurveTo(s * 6.4, -0.8, s * 8.3, 0.3);
            gc.quadraticCurveTo(s * 8.6, 1.3, s * 7.6, 1.9);
            gc.quadraticCurveTo(s * 5.6, 2.6, s * 3.6, 3.0);
            gc.closePath();
            gc.setFill(rgba(0.05, 0.07, 0.2, 0.35));
            gc.fill();
            gc.setStroke(rgba(0.45, 0.5, 0.9, 0.35));
            gc.setLineWidth(0.07);
            gc.stroke();

            // leading-edge highlight
            gc.beginPath();
            gc.moveTo(s * 2.6, -1.85);
            gc.quadraticCurveTo(s * 6.0, -1.45, s * 8.9, -0.2);
            gc.setStroke(rgba(0.6, 0.68, 1.0, 0.45));
            gc.setLineWidth(0.09);
            gc.stroke();

            // orange dash row
            gc.setStroke(ORANGE);
            gc.setLineWidth(0.22);
            int n = 7;
            for (int i = 0; i < n; i++) {
                double f0 = i / (double) n;
                double f1 = f0 + 0.5 / n;
                gc.strokeLine(s * lerp(8.7, 5.0, f0), lerp(0.9, 2.5, f0),
                        s * lerp(8.7, 5.0, f1), lerp(0.9, 2.5, f1));
            }
            gc.strokeLine(s * 8.4, -0.15, s * 8.8, 0.15);
        }
    }

    // ── 3. BULLET BARREL (left wing) ─────────────────────────────────────

    private void drawBulletBarrel(GraphicsContext gc, double flash) {
        double bx  = ShipGeometry.BULLET_MUZZLE_X;
        double tip = ShipGeometry.BULLET_MUZZLE_Y;

        // mount plate
        gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#12142e")),
                st(0.5, Color.web("#3a4280")), st(1, Color.web("#12142e"))));
        gc.fillRoundRect(bx - 0.85, -1.2, 1.7, 2.0, 0.5, 0.5);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.13);
        gc.strokeRoundRect(bx - 0.85, -1.2, 1.7, 2.0, 0.5, 0.5);

        // steel barrel
        gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#3c455c")),
                st(0.45, Color.web("#a9b6cc")), st(1, Color.web("#3c455c"))));
        gc.fillRoundRect(bx - 0.42, tip, 0.84, 3.2, 0.25, 0.25);
        gc.strokeRoundRect(bx - 0.42, tip, 0.84, 3.2, 0.25, 0.25);

        // orange ring + dark muzzle opening
        gc.setFill(ORANGE);
        gc.fillRoundRect(bx - 0.52, tip + 0.75, 1.04, 0.32, 0.12, 0.12);
        gc.setFill(Color.web("#06070f"));
        gc.fillOval(bx - 0.3, tip - 0.12, 0.6, 0.34);

        // muzzle flash
        if (flash > 0.02) {
            double fr = 1.0 + flash * 1.6;
            gc.setFill(rad(0, 0,
                    st(0.00, rgba(1.0, 1.0, 0.85, flash)),
                    st(0.35, rgba(1.0, 0.75, 0.2, 0.7 * flash)),
                    st(1.00, rgba(1.0, 0.4, 0.0, 0))));
            gc.fillOval(bx - fr, tip - 0.4 - fr, fr * 2, fr * 2);

            gc.setFill(rgba(1.0, 0.95, 0.7, flash * 0.9));
            gc.beginPath();
            gc.moveTo(bx - 0.25, tip);
            gc.lineTo(bx, tip - 0.4 - 2.2 * flash);
            gc.lineTo(bx + 0.25, tip);
            gc.closePath();
            gc.fill();
        }
    }

    // ── 4. LASER EMITTER (right wing, blue) ──────────────────────────────

    private void drawLaserEmitter(GraphicsContext gc, double charge, boolean armed) {
        double lx  = ShipGeometry.LASER_MUZZLE_X;
        double tip = ShipGeometry.LASER_MUZZLE_Y;

        // mount plate
        gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#12142e")),
                st(0.5, Color.web("#3a4280")), st(1, Color.web("#12142e"))));
        gc.fillRoundRect(lx - 0.85, -1.2, 1.7, 2.0, 0.5, 0.5);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.13);
        gc.strokeRoundRect(lx - 0.85, -1.2, 1.7, 2.0, 0.5, 0.5);

        // long slim emitter body
        gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#0d3a4a")),
                st(0.5, Color.web("#1fa6c4")), st(1, Color.web("#0d3a4a"))));
        gc.fillRoundRect(lx - 0.36, tip, 0.72, 4.2, 0.22, 0.22);
        gc.strokeRoundRect(lx - 0.36, tip, 0.72, 4.2, 0.22, 0.22);

        // energy coils brighten as charge builds
        double glow = Math.max(armed ? 0.35 : 0.12, charge);
        gc.setStroke(rgba(0.45, 0.95, 1.0, 0.35 + 0.65 * glow));
        gc.setLineWidth(0.17);
        for (int i = 0; i < 3; i++) {
            double y = tip + 1.0 + i * 0.95;
            gc.strokeLine(lx - 0.5, y, lx + 0.5, y);
        }

        // lens glow
        double lens = Math.min(1.0, (armed ? 0.22 : 0.08) + charge * 0.9);
        double gr   = 0.8 + charge * 2.8;
        gc.setFill(rad(0, 0,
                st(0.00, rgba(0.85, 1.0, 1.0, lens)),
                st(0.35, rgba(0.25, 0.75, 1.0, lens * 0.6)),
                st(1.00, rgba(0.1, 0.4, 1.0, 0))));
        gc.fillOval(lx - gr, tip - gr, gr * 2, gr * 2);

        double cr = 0.22 + charge * 0.32;
        gc.setFill(rgba(0.9, 1.0, 1.0, 0.45 + 0.55 * lens));
        gc.fillOval(lx - cr, tip - cr, cr * 2, cr * 2);
    }

    // ── 5. HULL ──────────────────────────────────────────────────────────

    private void hullPath(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(0, -7.9);
        gc.quadraticCurveTo(1.0, -7.9, 1.4, -6.9);
        gc.lineTo(1.8, -4.6);
        gc.quadraticCurveTo(2.0, -3.0, 2.7, -1.7);
        gc.lineTo(3.3, 1.5);
        gc.lineTo(3.2, 4.8);
        gc.quadraticCurveTo(3.0, 6.2, 1.7, 6.5);
        gc.lineTo(-1.7, 6.5);
        gc.quadraticCurveTo(-3.0, 6.2, -3.2, 4.8);
        gc.lineTo(-3.3, 1.5);
        gc.lineTo(-2.7, -1.7);
        gc.quadraticCurveTo(-2.0, -3.0, -1.8, -4.6);
        gc.lineTo(-1.4, -6.9);
        gc.quadraticCurveTo(-1.0, -7.9, 0, -7.9);
        gc.closePath();
    }

    private void drawHull(GraphicsContext gc) {
        hullPath(gc);
        gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#1f2459")),
                st(0.5, Color.web("#434b96")), st(1, Color.web("#1f2459"))));
        gc.fill();

        // top light / bottom shade, clipped to the hull
        gc.save();
        hullPath(gc);
        gc.clip();
        gc.setFill(lin(0, 0, 0, 1,
                st(0.0, rgba(1, 1, 1, 0.16)),
                st(0.5, rgba(1, 1, 1, 0.0)),
                st(1.0, rgba(0, 0, 0, 0.35))));
        gc.fillRect(-4, -8, 8, 15);
        gc.restore();

        hullPath(gc);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.18);
        gc.stroke();

        // nose plate + vents
        gc.beginPath();
        gc.moveTo(0, -7.3);
        gc.lineTo(1.0, -6.7);
        gc.lineTo(1.5, -4.7);
        gc.lineTo(-1.5, -4.7);
        gc.lineTo(-1.0, -6.7);
        gc.closePath();
        gc.setFill(rgba(0.45, 0.5, 0.95, 0.28));
        gc.fill();

        gc.setStroke(rgba(0.55, 0.6, 1.0, 0.5));
        gc.setLineWidth(0.12);
        gc.strokeLine(-0.65, -6.2, 0.65, -6.2);
        gc.strokeLine(-0.7, -5.75, 0.7, -5.75);

        // centre spine panel
        gc.setFill(rgba(0.04, 0.05, 0.16, 0.38));
        gc.fillRoundRect(-1.7, -1.35, 3.4, 5.2, 0.9, 0.9);
        gc.setStroke(rgba(0.5, 0.56, 1.0, 0.3));
        gc.setLineWidth(0.08);
        gc.strokeRoundRect(-1.7, -1.35, 3.4, 5.2, 0.9, 0.9);

        // three orange bars
        gc.setFill(ORANGE);
        for (int i = 0; i < 3; i++) {
            gc.fillRoundRect(-1.1, -0.1 + i * 0.5, 2.2, 0.32, 0.16, 0.16);
        }

        // cyan smile arc
        gc.setStroke(CYAN);
        gc.setLineWidth(0.2);
        gc.strokeArc(-1.5, 0.3, 3.0, 2.6, 205, 130, ArcType.OPEN);

        // small side module (right of the cockpit, like the reference)
        gc.setFill(Color.web("#1b1f4a"));
        gc.fillRect(1.6, -5.0, 0.8, 0.45);
        gc.setFill(Color.web("#2f3678"));
        gc.fillRoundRect(2.2, -5.6, 1.1, 1.25, 0.4, 0.4);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.12);
        gc.strokeRoundRect(2.2, -5.6, 1.1, 1.25, 0.4, 0.4);
        gc.setFill(ORANGE);
        gc.fillOval(2.53, -5.2, 0.44, 0.44);
    }

    // ── 6. ENGINE PODS + REAR BLOCK ──────────────────────────────────────

    private void drawEngines(GraphicsContext gc, boolean thrusting) {
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * ENGINE_X;

            gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#11142b")),
                    st(0.45, Color.web("#444c8e")), st(1, Color.web("#11142b"))));
            gc.fillRoundRect(cx - 1.15, 3.0, 2.3, 3.2, 0.8, 0.8);
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.14);
            gc.strokeRoundRect(cx - 1.15, 3.0, 2.3, 3.2, 0.8, 0.8);

            // heat band
            gc.setFill(lin(0, 0, 0, 1, st(0, Color.web("#ff9a2e")), st(1, Color.web("#b8321a"))));
            gc.fillRoundRect(cx - 0.95, 3.35, 1.9, 1.05, 0.35, 0.35);
            gc.setStroke(rgba(0.27, 0.08, 0.04, 0.55));
            gc.setLineWidth(0.1);
            for (int i = -1; i <= 1; i++) {
                gc.strokeLine(cx + i * 0.5, 3.45, cx + i * 0.5, 4.3);
            }

            // nozzle: dark mouth + cyan ring (brighter while thrusting)
            gc.setFill(Color.web("#06070f"));
            gc.fillOval(cx - 0.65, 5.7, 1.3, 0.45);
            gc.setStroke(thrusting ? Color.web("#7ff4ff") : CYAN_DEEP);
            gc.setLineWidth(0.28);
            gc.strokeOval(cx - 0.85, 5.55, 1.7, 0.7);
        }
    }

    private void drawRearBlock(GraphicsContext gc) {
        gc.setFill(lin(0, 0, 1, 0, st(0, Color.web("#0e1024")),
                st(0.5, Color.web("#2a3068")), st(1, Color.web("#0e1024"))));
        gc.fillRoundRect(-1.3, 3.6, 2.6, 3.0, 0.5, 0.5);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.14);
        gc.strokeRoundRect(-1.3, 3.6, 2.6, 3.0, 0.5, 0.5);

        gc.setStroke(rgba(0.47, 0.51, 0.78, 0.35));
        gc.setLineWidth(0.08);
        for (int i = 0; i < 5; i++) {
            double y = 4.2 + i * 0.45;
            gc.strokeLine(-0.95, y, 0.95, y);
        }
    }

    // ── 7. COCKPIT ───────────────────────────────────────────────────────

    private void drawCockpit(GraphicsContext gc) {
        double cy = -3.35, r = 1.65, collar = r + 0.3;

        gc.setFill(Color.web("#12163a"));
        gc.fillOval(-collar, cy - collar, collar * 2, collar * 2);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.16);
        gc.strokeOval(-collar, cy - collar, collar * 2, collar * 2);

        gc.setFill(rad(-135, 0.5,
                st(0.00, Color.web("#e6ffff")),
                st(0.22, Color.web("#5fe8f5")),
                st(0.60, Color.web("#1aa5bd")),
                st(1.00, Color.web("#0a566e"))));
        gc.fillOval(-r, cy - r, r * 2, r * 2);

        // glass highlights
        gc.setFill(rgba(1, 1, 1, 0.45));
        gc.fillOval(-r * 0.62, cy - r * 0.78, r * 0.85, r * 0.5);
        gc.setFill(rgba(1, 1, 1, 0.12));
        gc.fillOval(-r * 0.3, cy + r * 0.25, r * 1.2, r * 0.5);

        gc.setStroke(rgba(0.5, 0.94, 1.0, 0.6));
        gc.setLineWidth(0.08);
        gc.strokeOval(-r, cy - r, r * 2, r * 2);
    }

    // ── 8. LASER CHARGE RING (screen space) ──────────────────────────────

    private void drawChargeRing(GraphicsContext gc, double sx, double sy, double unit,
                                double charge, boolean full, double t) {
        gc.save();
        double pulse = full ? 1.0 + 0.05 * Math.sin(t * 9) : 1.0;
        double r = Math.max(unit * 11.0, 48.0) * (1.0 + charge * 0.18) * pulse;
        Color c = full ? Color.web("#bff3ff") : Color.web("#38b6ff");

        gc.setStroke(Color.color(c.getRed(), c.getGreen(), c.getBlue(), clamp(0.12 + 0.10 * charge)));
        gc.setLineWidth(10);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);

        gc.setStroke(Color.color(c.getRed(), c.getGreen(), c.getBlue(), full ? 0.95 : 0.8));
        gc.setLineWidth(2.5);
        gc.strokeArc(sx - r, sy - r, r * 2, r * 2, 90, -charge * 360, ArcType.OPEN);

        gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.9));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(full ? "READY" : (int) (charge * 100) + "%", sx, sy - r - 10);
        gc.restore();
    }
}