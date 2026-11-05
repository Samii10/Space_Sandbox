package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.entity.Asteroid;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.Star;
import project.aoop.space_sandbox.service.AsteroidService;
import project.aoop.space_sandbox.service.LevelService;
import project.aoop.space_sandbox.service.NavigationService;

import java.util.ArrayList;
import java.util.List;

@Component
public class SolarSystemMap {

    private boolean open = false;

    private static final Color ACCENT = Color.web("#4dffc8");
    private static final double NEARBY_RANGE = 600; // world units around the ship

    private final LevelService             levelService;
    private final ProceduralPlanetRenderer proceduralRenderer;
    private final AsteroidService          asteroidService;

    public SolarSystemMap(LevelService levelService,
                          ProceduralPlanetRenderer proceduralRenderer,
                          AsteroidService asteroidService) {
        this.levelService       = levelService;
        this.proceduralRenderer = proceduralRenderer;
        this.asteroidService    = asteroidService;
    }

    public void open()      { open = true; }
    public void close()     { open = false; }
    public void toggle()    { open = !open; }
    public boolean isOpen() { return open; }

    // ── helpers ────────────────────────────────────────────────────────────

    private double textWidth(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    private double computeMaxR(double w, double h, double maxDist) {
        double beltFactor = Math.log(maxDist * 1.70) / Math.log(maxDist);
        return (Math.min(w, h) / 2 - 70) / beltFactor;
    }

    private void drawCorner(GraphicsContext gc, double x, double y, double dx, double dy, double len) {
        gc.strokeLine(x, y, x + dx * len, y);
        gc.strokeLine(x, y, x, y + dy * len);
    }

    private static double c01(double v) { return Math.max(0, Math.min(1, v)); }

    // same grey as AsteroidRenderer.rockColor
    private static Color rockColor(double tint) {
        double g    = 0.60 + 0.22 * tint;
        double warm = (tint - 0.5) * 0.05;
        return Color.color(c01(g + warm), c01(g), c01(g - warm));
    }

    private List<Asteroid> snapshotAsteroids() {
        try {
            return new ArrayList<>(asteroidService.getAsteroids());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── render ─────────────────────────────────────────────────────────────

    public void render(GraphicsContext gc, double w, double h, Ship ship,
                       Star star, List<Planet> planets, NavigationService nav) {
        if (!open) return;

        double t = System.nanoTime() / 1e9;

        // ── Background ─────────────────────────────────────────────────────
        gc.setFill(Color.color(0, 0, 0.04, 0.94));
        gc.fillRect(0, 0, w, h);

        RadialGradient bg = new RadialGradient(0, 0, w / 2, h / 2, Math.max(w, h) * 0.6, false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.08, 0.14, 0.25, 0.35)),
                new Stop(1.0, Color.color(0, 0, 0, 0)));
        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);

        // ── Frame ──────────────────────────────────────────────────────────
        gc.setStroke(Color.color(1, 1, 1, 0.06));
        gc.setLineWidth(1);
        gc.strokeRect(20, 20, w - 40, h - 40);

        gc.setStroke(Color.color(0.3, 1, 0.8, 0.6));
        gc.setLineWidth(2);
        double cl = 22;
        drawCorner(gc, 20,     20,      1,  1, cl);
        drawCorner(gc, w - 20, 20,     -1,  1, cl);
        drawCorner(gc, 20,     h - 20,  1, -1, cl);
        drawCorner(gc, w - 20, h - 20, -1, -1, cl);

        // ── Title block (top right) ────────────────────────────────────────
        Font titleFont = Font.font("Courier New", FontWeight.BOLD, 20);
        Font hintFont  = Font.font("Courier New", 11);
        double tx = w - 40;

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(titleFont);
        gc.setFill(Color.color(1, 1, 1, 0.92));
        gc.fillText("SOLAR SYSTEM", tx, 56);

        double tw = textWidth("SOLAR SYSTEM", titleFont);
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.7));
        gc.setLineWidth(1.5);
        gc.strokeLine(tx - tw, 64, tx, 64);

        gc.setFont(hintFont);
        gc.setFill(Color.color(1, 1, 1, 0.45));
        gc.fillText("click planet to track", tx, 82);
        gc.fillText("ESC to close", tx, 97);

        // ── Layout ─────────────────────────────────────────────────────────
        double cx = w / 2;
        double cy = h / 2 - 10;

        double maxDist = planets.stream()
                .mapToDouble(Planet::getSemiMajorAxis)
                .max().orElse(6500);
        double maxR = computeMaxR(w, h, maxDist);

        java.util.function.DoubleUnaryOperator logScale = (worldDist) -> {
            if (worldDist <= 0) return 0;
            return (Math.log(worldDist) / Math.log(maxDist)) * maxR;
        };

        java.util.function.BiFunction<Double, Double, double[]> toMap = (wx, wy) -> {
            double dist = Math.sqrt(wx * wx + wy * wy);
            if (dist < 0.001) return new double[]{cx, cy};
            double scaled = logScale.applyAsDouble(dist);
            return new double[]{cx + (wx / dist) * scaled, cy + (wy / dist) * scaled};
        };

        String target = nav.getTarget();

        // ── Orbit rings ────────────────────────────────────────────────────
        for (Planet p : planets) {
            double r = logScale.applyAsDouble(p.getSemiMajorAxis());
            boolean acc = levelService.isAccessible(p.getName());
            boolean tgt = p.getName().equals(target);

            if (tgt) {
                gc.setStroke(Color.color(0.3, 1, 0.8, 0.35));
                gc.setLineWidth(1.2);
                gc.setLineDashes(6, 5);
            } else {
                gc.setStroke(Color.color(1, 1, 1, acc ? 0.12 : 0.06));
                gc.setLineWidth(0.6);
                gc.setLineDashes(2, 5);
            }
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }
        gc.setLineDashes((double[]) null);

        // ── Kuiper belt ────────────────────────────────────────────────────
        double kIn  = logScale.applyAsDouble(maxDist * 1.50);
        double kOut = Math.max(logScale.applyAsDouble(maxDist * 1.70), kIn + 14);
        proceduralRenderer.drawKuiperBeltMap(gc, cx, cy, kIn, kOut, t);

        // ── Sun ────────────────────────────────────────────────────────────
        RadialGradient sunGlow = new RadialGradient(0, 0, cx, cy, 38, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1, 0.85, 0.3, 0.55)),
                new Stop(0.4, Color.color(1, 0.65, 0.15, 0.2)),
                new Stop(1.0, Color.color(1, 0.5, 0.1, 0)));
        gc.setFill(sunGlow);
        gc.fillOval(cx - 38, cy - 38, 76, 76);
        gc.setFill(Color.web("#ffe066"));
        gc.fillOval(cx - 7, cy - 7, 14, 14);
        gc.setFill(Color.color(1, 1, 0.9, 0.9));
        gc.fillOval(cx - 3, cy - 3, 6, 6);

        // ── Ship position ──────────────────────────────────────────────────
        double[] sm = toMap.apply(ship.getX(), ship.getY());
        double sx   = Math.max(30, Math.min(w - 30, sm[0]));
        double sy   = Math.max(50, Math.min(h - 30, sm[1]));

        // ── Line from ship to tracked planet ───────────────────────────────
        for (Planet p : planets) {
            if (p.getName().equals(target)) {
                double[] mp = toMap.apply(p.getX(), p.getY());
                gc.setStroke(Color.color(0.3, 1, 0.8, 0.35));
                gc.setLineWidth(1);
                gc.setLineDashes(4, 6);
                gc.setLineDashOffset(-t * 12);
                gc.strokeLine(sx, sy, mp[0], mp[1]);
                gc.setLineDashes((double[]) null);
                gc.setLineDashOffset(0);
                break;
            }
        }

        // ── Asteroids ──────────────────────────────────────────────────────
        List<Asteroid> asteroids = snapshotAsteroids();
        int rockCount = 0, incomingCount = 0, nearbyCount = 0;

        // normal rocks first
        for (Asteroid a : asteroids) {
            if (a.getKind() == Asteroid.Kind.INCOMING) { incomingCount++; continue; }
            rockCount++;

            double dxs = a.getX() - ship.getX(), dys = a.getY() - ship.getY();
            if (Math.hypot(dxs, dys) < NEARBY_RANGE) nearbyCount++;

            double[] ap = toMap.apply(a.getX(), a.getY());
            double ax = Math.max(26, Math.min(w - 26, ap[0]));
            double ay = Math.max(26, Math.min(h - 26, ap[1]));

            double ar = Math.max(1.4, Math.min(3.2, a.getRadius() * 0.05));
            Color rc = rockColor(a.getTint());
            gc.setFill(Color.color(rc.getRed(), rc.getGreen(), rc.getBlue(), 0.85));
            gc.fillOval(ax - ar, ay - ar, ar * 2, ar * 2);
        }

        // incoming on top
        for (Asteroid a : asteroids) {
            if (a.getKind() != Asteroid.Kind.INCOMING) continue;

            double[] ap = toMap.apply(a.getX(), a.getY());
            double ax = Math.max(26, Math.min(w - 26, ap[0]));
            double ay = Math.max(26, Math.min(h - 26, ap[1]));

            double pulse = 0.5 + 0.5 * Math.sin(t * 6 + a.getTint() * 10);
            double ar = Math.max(2.4, Math.min(4.5, a.getRadius() * 0.07));

            // warning line to the ship
            gc.setStroke(Color.color(1, 0.3, 0.25, 0.25 + 0.2 * pulse));
            gc.setLineWidth(1);
            gc.setLineDashes(3, 5);
            gc.setLineDashOffset(-t * 20);
            gc.strokeLine(ax, ay, sx, sy);
            gc.setLineDashes((double[]) null);
            gc.setLineDashOffset(0);

            // trail
            double sp = Math.hypot(a.getVx(), a.getVy());
            if (sp > 1e-6) {
                double dx = a.getVx() / sp, dy = a.getVy() / sp;
                gc.setStroke(Color.color(1, 0.35, 0.3, 0.45));
                gc.setLineWidth(1.5);
                gc.strokeLine(ax, ay, ax - dx * 16, ay - dy * 16);
            }

            double gr = 10 + 4 * pulse;
            RadialGradient glow = new RadialGradient(0, 0, ax, ay, gr, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(1, 0.3, 0.25, 0.6)),
                    new Stop(1.0, Color.color(1, 0.3, 0.25, 0)));
            gc.setFill(glow);
            gc.fillOval(ax - gr, ay - gr, gr * 2, gr * 2);

            gc.setFill(Color.color(1, 0.55, 0.45, 0.98));
            gc.fillOval(ax - ar, ay - ar, ar * 2, ar * 2);
        }

        // ── Planets ────────────────────────────────────────────────────────
        Font nameFontBold   = Font.font("Courier New", FontWeight.BOLD, 10);
        Font nameFontNormal = Font.font("Courier New", FontWeight.NORMAL, 10);
        Font distFont       = Font.font("Courier New", 9);

        for (Planet p : planets) {
            double[] mp = toMap.apply(p.getX(), p.getY());
            double px   = mp[0];
            double py   = mp[1];

            double pr = Math.min(Math.max(p.getRadius() * 0.25, 8), 22);

            boolean accessible = levelService.isAccessible(p.getName());
            boolean isTarget   = p.getName().equals(target);

            if (accessible) {
                RadialGradient halo = new RadialGradient(0, 0, px, py, pr + 10, false, CycleMethod.NO_CYCLE,
                        new Stop(0.5, Color.color(0.5, 0.8, 1, 0.12)),
                        new Stop(1.0, Color.color(0.5, 0.8, 1, 0)));
                gc.setFill(halo);
                gc.fillOval(px - pr - 10, py - pr - 10, (pr + 10) * 2, (pr + 10) * 2);
            }

            gc.setGlobalAlpha(accessible ? 1.0 : 0.3);
            proceduralRenderer.drawAt(gc, p.getName(), px, py, pr, p.getColor());
            gc.setGlobalAlpha(1.0);

            if (isTarget) {
                double pulse = 0.5 + 0.5 * Math.sin(t * 3);
                double rr = pr + 6 + pulse * 3;
                gc.setStroke(Color.color(0.3, 1, 0.8, 0.9));
                gc.setLineWidth(2);
                gc.strokeOval(px - rr, py - rr, rr * 2, rr * 2);
                gc.setStroke(Color.color(0.3, 1, 0.8, 0.25));
                gc.setLineWidth(6);
                gc.strokeOval(px - rr, py - rr, rr * 2, rr * 2);
            }

            double realDist = Math.sqrt(
                    Math.pow(p.getX() - ship.getX(), 2) +
                            Math.pow(p.getY() - ship.getY(), 2));

            Font nf = accessible ? nameFontBold : nameFontNormal;
            double nw = textWidth(p.getName(), nf);
            gc.setFill(Color.color(0, 0, 0.06, 0.55));
            gc.fillRoundRect(px - nw / 2 - 5, py + pr + 4, nw + 10, 14, 8, 8);

            gc.setFill(accessible ? Color.color(1, 1, 1, 0.9) : Color.color(1, 1, 1, 0.35));
            gc.setFont(nf);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(p.getName(), px, py + pr + 14);

            gc.setFill(Color.color(0.6, 0.85, 1, 0.45));
            gc.setFont(distFont);
            gc.fillText((int) realDist + " u", px, py + pr + 26);

            if (!accessible) {
                gc.setFill(Color.color(1, 0.4, 0.4, 0.7));
                gc.fillText("LOCKED", px, py + pr + 37);
            }
        }

        // ── Ship marker ────────────────────────────────────────────────────
        if (nearbyCount > 0) {
            double pulse = 0.5 + 0.5 * Math.sin(t * 2);
            double nr = 20 + 3 * pulse;
            gc.setStroke(Color.color(1, 0.75, 0.3, 0.6));
            gc.setLineWidth(1);
            gc.setLineDashes(3, 4);
            gc.strokeOval(sx - nr, sy - nr, nr * 2, nr * 2);
            gc.setLineDashes((double[]) null);

            gc.setFill(Color.color(1, 0.8, 0.4, 0.9));
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 9));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(nearbyCount + " NEARBY", sx, sy - nr - 5);
        }

        RadialGradient shipGlow = new RadialGradient(0, 0, sx, sy, 16, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.3, 1, 0.8, 0.4)),
                new Stop(1.0, Color.color(0.3, 1, 0.8, 0)));
        gc.setFill(shipGlow);
        gc.fillOval(sx - 16, sy - 16, 32, 32);

        gc.save();
        gc.translate(sx, sy);
        gc.rotate(Math.toDegrees(ship.getAngle()));
        gc.setFill(ACCENT);
        gc.fillPolygon(new double[]{8, -5, -5}, new double[]{0, -5, 5}, 3);
        gc.restore();

        gc.setFill(Color.color(0.3, 1, 0.8, 0.85));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("YOU", sx + 12, sy + 4);

        // ── Tracking panel (bottom right) ──────────────────────────────────
        Font trackFont = Font.font("Courier New", FontWeight.BOLD, 13);
        String trackText = "TRACKING → " + target;
        double trw = textWidth(trackText, trackFont);
        double panelW = trw + 24;
        double panelX = w - 30 - panelW;
        double panelY = h - 62;

        gc.setFill(Color.color(0, 0.1, 0.1, 0.6));
        gc.fillRoundRect(panelX, panelY, panelW, 28, 10, 10);
        gc.setStroke(Color.color(0.3, 1, 0.8, 0.5));
        gc.setLineWidth(1);
        gc.strokeRoundRect(panelX, panelY, panelW, 28, 10, 10);

        gc.setFill(Color.color(0.3, 1, 0.8, 0.9));
        gc.setFont(trackFont);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(trackText, w - 42, h - 43);

        // ── Legend panel (bottom left) ─────────────────────────────────────
        double lx = 40, lw = 210, lh = 104, ly = h - 40 - lh;

        gc.setFill(Color.color(0, 0, 0.06, 0.78));
        gc.fillRoundRect(lx, ly, lw, lh, 10, 10);
        gc.setStroke(Color.color(1, 1, 1, 0.15));
        gc.setLineWidth(1);
        gc.strokeRoundRect(lx, ly, lw, lh, 10, 10);

        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 9));
        gc.setFill(Color.color(1, 1, 1, 0.4));
        gc.fillText("LEGEND  ·  LOG SCALE", lx + 12, ly + 16);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 11));

        gc.setFill(Color.color(0.75, 0.75, 0.78, 0.9));
        gc.fillOval(lx + 14, ly + 28, 7, 7);
        gc.setFill(Color.color(1, 1, 1, 0.8));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("ASTEROID", lx + 30, ly + 36);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(String.valueOf(rockCount), lx + lw - 12, ly + 36);

        gc.setFill(Color.color(1, 0.4, 0.35, 0.95));
        gc.fillOval(lx + 14, ly + 46, 7, 7);
        gc.setFill(Color.color(1, 0.5, 0.45, 0.95));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("INCOMING", lx + 30, ly + 54);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(String.valueOf(incomingCount), lx + lw - 12, ly + 54);

        gc.setFill(Color.color(0.3, 1, 0.8, 0.95));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("✕", lx + 13, ly + 72);
        gc.fillText("DESTROYED", lx + 30, ly + 72);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(String.valueOf(asteroidService.getDestroyedCount()), lx + lw - 12, ly + 72);

        if (asteroids.isEmpty()) {
            gc.setFont(Font.font("Courier New", 9));
            gc.setFill(Color.color(1, 1, 1, 0.35));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("NO ASTEROIDS ACTIVE", lx + 14, ly + 92);
        }

        gc.setTextAlign(TextAlignment.LEFT);
    }

    // Returns true if a planet was selected
    public boolean handleClick(double mouseX, double mouseY, double w, double h,
                               List<Planet> planets, NavigationService nav) {
        if (!open) return false;

        double cx      = w / 2;
        double cy      = h / 2 - 10;
        double maxDist = planets.stream()
                .mapToDouble(Planet::getSemiMajorAxis)
                .max().orElse(6500);
        double maxR    = computeMaxR(w, h, maxDist);

        Planet closest  = null;
        double closestD = Double.MAX_VALUE;

        for (Planet p : planets) {
            double dist   = Math.sqrt(p.getX() * p.getX() + p.getY() * p.getY());
            double scaled = dist < 0.001 ? 0
                    : (Math.log(dist) / Math.log(maxDist)) * maxR;
            double px     = cx + (p.getX() / (dist < 0.001 ? 1 : dist)) * scaled;
            double py     = cy + (p.getY() / (dist < 0.001 ? 1 : dist)) * scaled;
            double hitR   = Math.max(Math.max(p.getRadius() * 0.25, 8), 18);
            double d      = Math.sqrt((mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py));

            if (d < hitR && d < closestD) {
                closestD = d;
                closest  = p;
            }
        }

        if (closest != null) {
            nav.setTarget(closest.getName());
            System.out.println("📍 Tracking: " + closest.getName());
        }

        close();
        return closest != null;
    }
}