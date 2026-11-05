package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.service.LevelService;
import project.aoop.space_sandbox.service.NavigationService;

import java.util.ArrayList;
import java.util.List;

@Component
public class HUDRenderer {

    private static final Color ACCENT = Color.web("#4dffc8");

    private final GameCamera       camera;
    private final LevelService     levelService;
    private final NavigationService navigationService;

    public HUDRenderer(GameCamera camera, LevelService levelService,
                       NavigationService navigationService) {
        this.camera            = camera;
        this.levelService      = levelService;
        this.navigationService = navigationService;
    }

    public void render(GraphicsContext gc, Ship ship, List<Planet> planets) {
        drawLevelPanel(gc);
        drawTargetArrow(gc, ship, planets);
        drawSpeedBar(gc, ship);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private double textWidth(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    private void drawCorner(GraphicsContext gc, double x, double y, double dx, double dy, double len) {
        gc.strokeLine(x, y, x + dx * len, y);
        gc.strokeLine(x, y, x, y + dy * len);
    }

    // Planet name gulo panel-er width onujayi line-e bhag kore
    private List<String> wrapNames(List<String> names, Font f, double maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String n : names) {
            String test = cur.length() == 0 ? n : cur + "  " + n;
            if (textWidth(test, f) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(n);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ── Level panel (top-left) ─────────────────────────────────────────────

    private void drawLevelPanel(GraphicsContext gc) {
        double x = 14, y = 14, pw = 240;

        Font listFont = Font.font("Courier New", 10);
        List<String> lines = wrapNames(
                new ArrayList<>(levelService.getAccessiblePlanets()), listFont, pw - 32);

        double ph = 66 + lines.size() * 14 + (levelService.hasLaser() ? 22 : 8);

        // background + soft glow
        gc.setFill(Color.color(0, 0, 0.05, 0.82));
        gc.fillRoundRect(x, y, pw, ph, 14, 14);

        RadialGradient glow = new RadialGradient(0, 0, x + 40, y + 30, pw * 0.8, false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.08, 0.2, 0.25, 0.45)),
                new Stop(1.0, Color.color(0, 0, 0, 0)));
        gc.setFill(glow);
        gc.fillRoundRect(x, y, pw, ph, 14, 14);

        // border + teal corner brackets
        gc.setStroke(Color.color(1, 1, 1, 0.10));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, pw, ph, 14, 14);

        gc.setStroke(Color.color(0.3, 1, 0.8, 0.6));
        gc.setLineWidth(1.5);
        drawCorner(gc, x + 6,      y + 6,      1,  1, 12);
        drawCorner(gc, x + pw - 6, y + ph - 6, -1, -1, 12);

        // small heading
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Courier New", 9));
        gc.setFill(Color.color(1, 1, 1, 0.35));
        gc.fillText("SHIP LEVEL", x + 18, y + 24);

        // big level text
        Font lvlFont = Font.font("Courier New", FontWeight.BOLD, 24);
        String lvl = "LEVEL " + levelService.getLevel();
        gc.setFont(lvlFont);
        gc.setFill(ACCENT);
        gc.fillText(lvl, x + 18, y + 50);

        // fading accent underline
        double uw = Math.max(textWidth(lvl, lvlFont), 90);
        LinearGradient line = new LinearGradient(x + 18, 0, x + 18 + uw, 0, false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.3, 1, 0.8, 0.8)),
                new Stop(1.0, Color.color(0.3, 1, 0.8, 0)));
        gc.setStroke(line);
        gc.setLineWidth(1.5);
        gc.strokeLine(x + 18, y + 57, x + 18 + uw, y + 57);

        // unlocked planets
        gc.setFont(listFont);
        gc.setFill(Color.color(0.7, 0.9, 1, 0.6));
        double ly = y + 74;
        for (String line1 : lines) {
            gc.fillText(line1, x + 18, ly);
            ly += 14;
        }

        if (levelService.hasLaser()) {
            gc.setFill(Color.color(1, 0.27, 0.27, 0.9));
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
            gc.fillText("⚡ LASER UNLOCKED", x + 18, ly + 4);
        }
    }

    // ── Target arrow ───────────────────────────────────────────────────────

    private void drawTargetArrow(GraphicsContext gc, Ship ship, List<Planet> planets) {
        String targetName = navigationService.getTarget();
        Planet target = planets.stream()
                .filter(p -> p.getName().equals(targetName))
                .findFirst().orElse(null);
        if (target == null) return;

        double W = camera.getScreenW(), H = camera.getScreenH();

        // Anchor to where the ship is actually drawn
        double cx = camera.toScreenX(ship.getX());
        double cy = camera.toScreenY(ship.getY());

        double dx    = target.getX() - ship.getX();
        double dy    = target.getY() - ship.getY();
        double dist  = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.atan2(dy, dx);

        double tsx = camera.toScreenX(target.getX());
        double tsy = camera.toScreenY(target.getY());
        double tr  = target.getRadius() * camera.getZoom();
        boolean visible = tsx + tr > 0 && tsx - tr < W && tsy + tr > 0 && tsy - tr < H;

        if (visible) {
            // planet is on screen: pulsing ring around it
            double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * 3);
            double pr = tr + 12 + pulse * 3;
            gc.setStroke(Color.color(0.3, 1, 0.8, 0.2));
            gc.setLineWidth(6);
            gc.strokeOval(tsx - pr, tsy - pr, pr * 2, pr * 2);
            gc.setStroke(Color.color(0.3, 1, 0.8, 0.6));
            gc.setLineWidth(1.5);
            gc.strokeOval(tsx - pr, tsy - pr, pr * 2, pr * 2);
            return;
        }

        // off screen: arrow sits just outside the ship's wings, scaled to ship size
        double shipRadiusPx = ShipGeometry.WORLD_PER_UNIT * camera.getZoom() * 10.5;
        double ringDist = Math.min(shipRadiusPx + 44, Math.min(W, H) * 0.5 - 60);
        ringDist = Math.max(ringDist, 60);

        double ax = cx + Math.cos(angle) * ringDist;
        double ay = cy + Math.sin(angle) * ringDist;

        // arrow glow
        RadialGradient aGlow = new RadialGradient(0, 0, ax, ay, 22, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.3, 1, 0.8, 0.35)),
                new Stop(1.0, Color.color(0.3, 1, 0.8, 0)));
        gc.setFill(aGlow);
        gc.fillOval(ax - 22, ay - 22, 44, 44);

        gc.save();
        gc.translate(ax, ay);
        gc.rotate(Math.toDegrees(angle));
        gc.setFill(Color.color(0.3, 1, 0.8, 0.95));
        gc.fillPolygon(new double[]{14, -6, -6}, new double[]{0, -5, 5}, 3);
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.4));
        gc.setLineWidth(2);
        gc.strokeLine(-6, 0, -16, 0);
        gc.restore();

        // label pill
        double lx = ax + Math.cos(angle) * 40;
        double ly = ay + Math.sin(angle) * 40;
        Font nameFont = Font.font("Courier New", FontWeight.BOLD, 12);
        String name = "→ " + target.getName();
        double nw = textWidth(name, nameFont);

        gc.setFill(Color.color(0, 0, 0.06, 0.55));
        gc.fillRoundRect(lx - nw / 2 - 8, ly - 17, nw + 16, 32, 10, 10);
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.3));
        gc.setLineWidth(1);
        gc.strokeRoundRect(lx - nw / 2 - 8, ly - 17, nw + 16, 32, 10, 10);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.color(0.3, 1, 0.8, 0.95));
        gc.setFont(nameFont);
        gc.fillText(name, lx, ly - 3);
        gc.setFill(Color.color(0.6, 0.85, 1, 0.6));
        gc.setFont(Font.font("Courier New", 10));
        gc.fillText((int) dist + " u", lx, ly + 10);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ── Lock warnings (same as before) ─────────────────────────────────────

    public void drawLockWarnings(GraphicsContext gc, Ship ship, List<Planet> planets) {
        for (Planet p : planets) {
            if (!levelService.isLocked(p.getName())) continue;

            double dx   = ship.getX() - p.getX();
            double dy   = ship.getY() - p.getY();
            double dist = Math.sqrt(dx*dx + dy*dy);

            double zone    = p.getGravityRadius();
            boolean inside = dist < zone;
            boolean close  = dist < zone * 1.8;

            if (!close) continue;

            double cx = camera.getScreenW() / 2;
            double cy = camera.getScreenH() / 2;

            double severity = 1.0 - ((dist - zone) / (zone * 0.8));
            severity = Math.max(0, Math.min(1, severity));

            double pulse = 0.6 + 0.4 * Math.sin(
                    System.currentTimeMillis() * (0.004 + severity * 0.012));

            if (inside) {
                for (int i = 0; i < 3; i++) {
                    double margin = i * 12.0;
                    gc.setStroke(Color.color(1, 0.1, 0.1,
                            0.08 * pulse * (3 - i)));
                    gc.setLineWidth(20 - i * 6);
                    gc.strokeRect(margin, margin,
                            camera.getScreenW() - margin*2,
                            camera.getScreenH() - margin*2);
                }

                double bw = 420, bh = 52;
                double bx = cx - bw/2, by = 60;

                gc.setFill(Color.color(0.6, 0, 0, 0.75 * pulse));
                gc.fillRoundRect(bx, by, bw, bh, 6, 6);

                gc.setStroke(Color.color(1, 0.2, 0.2, pulse));
                gc.setLineWidth(1.5);
                gc.strokeRoundRect(bx, by, bw, bh, 6, 6);

                gc.setFill(Color.color(1, 1, 1, pulse));
                gc.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("⚠  LOCKED ZONE — " + p.getName().toUpperCase()
                        + "  ⚠", cx, by + 33);
                gc.setTextAlign(TextAlignment.LEFT);

                double surface = dist - p.getRadius();
                gc.setFill(Color.color(1, 0.5, 0.5, 0.85));
                gc.setFont(Font.font("Courier New", 11));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.format("%.0f u to surface — THRUST AWAY NOW",
                        Math.max(0, surface)), cx, by + bh + 16);
                gc.setTextAlign(TextAlignment.LEFT);
            } else {
                double angle = Math.atan2(dy, dx);
                double ww = 260, wh = 44;

                double wx, wy;
                if (Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle))) {
                    wx = Math.cos(angle) > 0
                            ? camera.getScreenW() - ww - 12
                            : 12;
                    wy = cy - wh / 2;
                } else {
                    wx = cx - ww / 2;
                    wy = Math.sin(angle) > 0
                            ? camera.getScreenH() - wh - 50
                            : 115;
                }

                gc.setFill(Color.color(0.3, 0, 0, 0.70 * pulse));
                gc.fillRoundRect(wx, wy, ww, wh, 6, 6);

                gc.setStroke(Color.color(1, 0.2, 0.2, 0.5 * pulse));
                gc.setLineWidth(1);
                gc.strokeRoundRect(wx, wy, ww, wh, 6, 6);

                gc.setFill(Color.color(1, 0.4, 0.4, pulse));
                gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("⚠  " + p.getName().toUpperCase() + " — LOCKED",
                        wx + ww/2, wy + 17);

                double toZone = dist - zone;
                gc.setFill(Color.color(1, 0.7, 0.7, 0.8 * pulse));
                gc.setFont(Font.font("Courier New", 10));
                gc.fillText(String.format("%.0f u to danger zone", toZone),
                        wx + ww/2, wy + 32);
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }
    }

    // ── Speed bar (bottom center) ──────────────────────────────────────────

    private void drawSpeedBar(GraphicsContext gc, Ship ship) {
        double cx = camera.getScreenW() / 2;
        double bw = 190, bh = 34;
        double bx = cx - bw / 2;
        double by = camera.getScreenH() - 16 - bh;

        gc.setFill(Color.color(0, 0, 0.05, 0.8));
        gc.fillRoundRect(bx, by, bw, bh, 17, 17);
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.35));
        gc.setLineWidth(1);
        gc.strokeRoundRect(bx, by, bw, bh, 17, 17);

        // speed fill line along the bottom edge (max ~ 100 u/s)
        double frac = Math.max(0, Math.min(1, ship.getSpeed() / 100.0));
        double fw   = (bw - 30) * frac;
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.8));
        gc.setLineWidth(2);
        gc.strokeLine(bx + 15, by + bh - 4, bx + 15 + fw, by + bh - 4);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        gc.setFill(Color.color(1, 1, 1, 0.4));
        gc.fillText("SPD", bx + 16, by + 20);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 15));
        gc.setFill(ACCENT);
        gc.fillText(String.format("%.2f", ship.getSpeed()), cx + 4, by + 21);

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(Font.font("Courier New", 10));
        gc.setFill(Color.color(1, 1, 1, 0.4));
        gc.fillText("u/s", bx + bw - 16, by + 20);

        gc.setTextAlign(TextAlignment.LEFT);
    }
}