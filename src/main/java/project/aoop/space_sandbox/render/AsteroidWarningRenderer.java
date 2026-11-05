package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.Asteroid;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.service.AsteroidService;

import java.util.List;

/**
 * Draw this LAST so nothing covers it.
 */
@Component
public class AsteroidWarningRenderer {

    private final GameCamera      camera;
    private final AsteroidService asteroidService;


    private double  btnX, btnY, btnW = 240, btnH = 46;
    private boolean btnVisible = false;

    public AsteroidWarningRenderer(GameCamera camera, AsteroidService asteroidService) {
        this.camera = camera;
        this.asteroidService = asteroidService;
    }


    public boolean isRestartButtonVisible() { return btnVisible; }

    public boolean isRestartButtonHit(double mx, double my) {
        return btnVisible && mx >= btnX && mx <= btnX + btnW
                && my >= btnY && my <= btnY + btnH;
    }

    public void resetAnimation() { btnVisible = false; }

    // ── main render ──────────────────────────────────────────────────────

    public void render(GraphicsContext gc, Ship ship) {
        gc.save();
        double W = camera.getScreenW();
        double H = camera.getScreenH();
        double t = System.nanoTime() * 1e-9;

        drawHitFlash(gc, W, H);
        drawBeltNotice(gc);
        drawHullBar(gc, ship, H);

        List<AsteroidService.Threat> threats = asteroidService.getThreats();
        if (!threats.isEmpty() && ship.getHealth() > 0) {
            double eta     = threats.get(0).eta;
            double urgency = clamp01(1.0 - eta / 6.0);
            double pulse   = 0.55 + 0.45 * Math.sin(t * (6 + urgency * 10));

            if (urgency > 0) drawEdgeGlow(gc, W, H, urgency, pulse);
            drawThreatMarkers(gc, threats, W, H, pulse);
            drawThreatBanner(gc, threats, W, pulse);
        }

        if (ship.getHealth() <= 0) {
            drawDestroyed(gc, W, H);
        } else {
            btnVisible = false;
        }
        gc.restore();
    }

    // ── banner: "DANGER — 2 ASTEROIDS INCOMING" ──────────────────────────

    private void drawThreatBanner(GraphicsContext gc, List<AsteroidService.Threat> threats,
                                  double W, double pulse) {
        int n = threats.size();
        AsteroidService.Threat next = threats.get(0);

        double bw = 420, bh = 50;
        double bx = W / 2 - bw / 2, by = 16;

        gc.setFill(Color.color(0.45, 0.0, 0.0, 0.55 + 0.25 * pulse));
        gc.fillRoundRect(bx, by, bw, bh, 8, 8);
        gc.setStroke(Color.color(1.0, 0.25, 0.2, 0.5 + 0.5 * pulse));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(bx, by, bw, bh, 8, 8);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.color(1, 1, 1, 0.75 + 0.25 * pulse));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 16));
        gc.fillText(n == 1 ? "⚠  DANGER — ASTEROID INCOMING"
                : "⚠  DANGER — " + n + " ASTEROIDS INCOMING", W / 2, by + 22);

        gc.setFill(Color.color(1.0, 0.75, 0.7, 0.9));
        gc.setFont(Font.font("Courier New", 12));
        gc.fillText(String.format("impact in %.1f s  ·  %d u away  ·  shoot it or change course",
                next.eta, (int) next.distance), W / 2, by + 40);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawThreatMarkers(GraphicsContext gc, List<AsteroidService.Threat> threats,
                                   double W, double H, double pulse) {
        int shown = 0;
        for (AsteroidService.Threat th : threats) {
            if (shown++ >= 6) break;

            Asteroid a = th.asteroid;
            double sx = camera.toScreenX(a.getX());
            double sy = camera.toScreenY(a.getY());
            double r  = a.getRadius() * camera.getZoom();

            boolean onScreen = sx > 0 && sx < W && sy > 0 && sy < H;
            gc.setTextAlign(TextAlignment.CENTER);

            if (onScreen) {
                double rr = Math.max(r, 6) + 10 + 3 * pulse;
                gc.setStroke(Color.color(1, 0.15, 0.1, 0.55 + 0.4 * pulse));
                gc.setLineWidth(2);
                gc.strokeOval(sx - rr, sy - rr, rr * 2, rr * 2);

                gc.setFill(Color.color(1, 0.35, 0.3, 0.95));
                gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
                gc.fillText(String.format("%.1fs", th.eta), sx, sy - rr - 6);
            } else {
                double cx = W / 2, cy = H / 2;
                double dx = sx - cx, dy = sy - cy;
                double halfW = W / 2 - 48, halfH = H / 2 - 48;
                double k = Math.min(halfW / Math.max(Math.abs(dx), 1e-6),
                        halfH / Math.max(Math.abs(dy), 1e-6));
                double ex  = cx + dx * k, ey = cy + dy * k;
                double ang = Math.atan2(dy, dx);

                gc.save();
                gc.translate(ex, ey);
                gc.rotate(Math.toDegrees(ang));
                gc.setFill(Color.color(1.0, 0.18, 0.12, 0.55 + 0.45 * pulse));
                gc.fillPolygon(new double[]{16, -9, -4, -9}, new double[]{0, -10, 0, 10}, 4);
                gc.restore();

                double tx = ex - Math.cos(ang) * 38, ty = ey - Math.sin(ang) * 38;
                gc.setFill(Color.color(1, 0.4, 0.35, 0.95));
                gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
                gc.fillText(String.format("%.1fs", th.eta), tx, ty);
                gc.setFill(Color.color(1, 0.75, 0.7, 0.7));
                gc.setFont(Font.font("Courier New", 10));
                gc.fillText((int) th.distance + " u", tx, ty + 12);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawEdgeGlow(GraphicsContext gc, double W, double H,
                              double urgency, double pulse) {
        double a = 0.10 + 0.30 * urgency;
        for (int i = 0; i < 3; i++) {
            double m = i * 10.0;
            gc.setStroke(Color.color(1, 0.1, 0.05, a * pulse * (3 - i) / 3.0));
            gc.setLineWidth(18 - i * 5);
            gc.strokeRect(m, m, W - m * 2, H - m * 2);
        }
    }

    private void drawHitFlash(GraphicsContext gc, double W, double H) {
        double f = asteroidService.getHitFlash();
        if (f <= 0) return;
        gc.setFill(Color.color(1, 0.05, 0.0, 0.28 * f));
        gc.fillRect(0, 0, W, H);
        gc.setStroke(Color.color(1, 0.1, 0.05, 0.6 * f));
        gc.setLineWidth(14);
        gc.strokeRect(7, 7, W - 14, H - 14);
    }

    private void drawHullBar(GraphicsContext gc, Ship ship, double H) {
        double x = 16, y = H - 40, w = 170, h = 10;
        double ratio = clamp01(ship.getHealth() / (double) AsteroidService.MAX_HULL);

        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(x - 8, y - 24, w + 16, 44, 8, 8);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.color(1, 1, 1, 0.6));
        gc.setFont(Font.font("Courier New", 11));
        gc.fillText("HULL  " + (int) Math.round(ratio * 100) + "%", x, y - 8);

        gc.setFill(Color.color(1, 1, 1, 0.10));
        gc.fillRoundRect(x, y, w, h, 5, 5);

        Color c = ratio > 0.6 ? Color.color(0.3, 1.0, 0.5, 0.9)
                : ratio > 0.3 ? Color.color(1.0, 0.85, 0.2, 0.9)
                : Color.color(1.0, 0.3, 0.2, 0.9);
        gc.setFill(c);
        gc.fillRoundRect(x, y, w * ratio, h, 5, 5);
    }
    private void drawBeltNotice(GraphicsContext gc) {
        if (!asteroidService.isInBelt()) return;

        double x = 14, y = 172, w = 240, h = 44;

        gc.setFill(Color.color(0, 0, 0.05, 0.82));
        gc.fillRoundRect(x, y, w, h, 12, 12);
        gc.setStroke(Color.color(1.0, 0.75, 0.3, 0.40));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 12, 12);

        gc.setFill(Color.color(1.0, 0.75, 0.3, 0.9));
        gc.fillRoundRect(x, y + 8, 3, h - 16, 3, 3);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.color(1.0, 0.78, 0.35, 0.95));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
        gc.fillText("ASTEROID BELT", x + 14, y + 19);

        gc.setFill(Color.color(1, 1, 1, 0.6));
        gc.setFont(Font.font("Courier New", 11));
        gc.fillText("destroyed: " + asteroidService.getDestroyedCount(), x + 14, y + 35);
    }

    private void drawDestroyed(GraphicsContext gc, double W, double H) {
        gc.setFill(Color.color(0.25, 0, 0, 0.55));
        gc.fillRect(0, 0, W, H);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.color(1, 0.25, 0.2, 0.95));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 44));
        gc.fillText("SHIP DESTROYED", W / 2, H / 2 - 6);
        gc.setFill(Color.color(1, 1, 1, 0.6));
        gc.setFont(Font.font("Courier New", 14));
        gc.fillText("Restart the game to try again", W / 2, H / 2 + 28);

        // ── restart button ──
        btnVisible = true;
        btnW = 240;
        btnH = 46;
        btnX = W / 2 - btnW / 2;
        btnY = H / 2 + 52;

        double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() * 1e-9 * 3);

        gc.setStroke(Color.color(0.3, 1, 0.8, 0.10 + 0.12 * pulse));
        gc.setLineWidth(8);
        gc.strokeRoundRect(btnX, btnY, btnW, btnH, btnH, btnH);

        gc.setFill(Color.color(0, 0.14, 0.13, 0.95));
        gc.fillRoundRect(btnX, btnY, btnW, btnH, btnH, btnH);
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.6 + 0.4 * pulse));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(btnX, btnY, btnW, btnH, btnH, btnH);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
        gc.setFill(Color.color(0.3, 1, 0.8, 1));
        gc.fillText("↻  RESTART", W / 2, btnY + 30);

        gc.setFont(Font.font("Courier New", 11));
        gc.setFill(Color.color(1, 1, 1, 0.4));
        gc.fillText("click  or  press  R", W / 2, btnY + btnH + 20);

        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}