package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.entity.Asteroid;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.Star;
import project.aoop.space_sandbox.service.AsteroidService;

import java.util.ArrayList;
import java.util.List;

@Component
public class MinimapRenderer {

    private static final double SIZE       = 180;
    private static final double PADDING    = 14;
    private static final double MAX_RADIUS = SIZE / 2 - 8; // usable radius in pixels
    private static final double MAX_DIST   = 6500;          // Neptune's orbit

    private static final Color ACCENT = Color.web("#4dffc8");

    private final ProceduralPlanetRenderer proceduralRenderer;
    private final AsteroidService          asteroidService;

    public MinimapRenderer(ProceduralPlanetRenderer proceduralRenderer,
                           AsteroidService asteroidService) {
        this.proceduralRenderer = proceduralRenderer;
        this.asteroidService    = asteroidService;
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

    public void render(GraphicsContext gc, double canvasW, double canvasH,
                       Ship ship, Star star, List<Planet> planets) {

        double t   = System.nanoTime() / 1e9;
        double mx  = canvasW - SIZE - PADDING;
        double my  = PADDING;
        double mcx = mx + SIZE / 2;
        double mcy = my + SIZE / 2;

        // ── Background panel + soft center glow ────────────────────────────
        gc.setFill(Color.color(0, 0, 0.05, 0.85));
        gc.fillRoundRect(mx, my, SIZE, SIZE, 14, 14);

        RadialGradient bg = new RadialGradient(0, 0, mcx, mcy, SIZE * 0.7, false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.08, 0.14, 0.25, 0.45)),
                new Stop(1.0, Color.color(0, 0, 0, 0)));
        gc.setFill(bg);
        gc.fillRoundRect(mx, my, SIZE, SIZE, 14, 14);

        // Border + teal corner accents
        gc.setStroke(Color.color(1, 1, 1, 0.10));
        gc.setLineWidth(1);
        gc.strokeRoundRect(mx, my, SIZE, SIZE, 14, 14);

        gc.setStroke(Color.color(0.3, 1, 0.8, 0.6));
        gc.setLineWidth(1.5);
        double cl = 12;
        gc.strokeLine(mx + 6,        my + 6,        mx + 6 + cl,        my + 6);
        gc.strokeLine(mx + 6,        my + 6,        mx + 6,             my + 6 + cl);
        gc.strokeLine(mx + SIZE - 6, my + SIZE - 6, mx + SIZE - 6 - cl, my + SIZE - 6);
        gc.strokeLine(mx + SIZE - 6, my + SIZE - 6, mx + SIZE - 6,      my + SIZE - 6 - cl);

        // Clip to minimap box
        gc.save();
        gc.beginPath();
        gc.rect(mx + 1, my + 1, SIZE - 2, SIZE - 2);
        gc.clip();

        // ── Orbit rings (dotted, log scaled) ───────────────────────────────
        gc.setLineWidth(0.6);
        gc.setLineDashes(2, 4);
        for (Planet p : planets) {
            double logR = logScale(p.getSemiMajorAxis());
            gc.setStroke(Color.color(1, 1, 1, 0.10));
            gc.strokeOval(mcx - logR, mcy - logR, logR * 2, logR * 2);
        }
        gc.setLineDashes((double[]) null);

        // ── Sun with glow ──────────────────────────────────────────────────
        RadialGradient sunGlow = new RadialGradient(0, 0, mcx, mcy, 16, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1, 0.85, 0.3, 0.55)),
                new Stop(0.4, Color.color(1, 0.65, 0.15, 0.2)),
                new Stop(1.0, Color.color(1, 0.5, 0.1, 0)));
        gc.setFill(sunGlow);
        gc.fillOval(mcx - 16, mcy - 16, 32, 32);
        gc.setFill(Color.web("#ffe066"));
        gc.fillOval(mcx - 3.5, mcy - 3.5, 7, 7);
        gc.setFill(Color.color(1, 1, 0.9, 0.9));
        gc.fillOval(mcx - 1.5, mcy - 1.5, 3, 3);

        // ── Ship position (needed for incoming warning lines) ──────────────
        double[] sm     = worldToMinimap(ship.getX(), ship.getY(), mcx, mcy);
        double rawSx    = sm[0];
        double rawSy    = sm[1];
        double sx       = Math.max(mx + 5, Math.min(mx + SIZE - 5, rawSx));
        double sy       = Math.max(my + 5, Math.min(my + SIZE - 5, rawSy));
        boolean offEdge = rawSx < mx || rawSx > mx + SIZE || rawSy < my || rawSy > my + SIZE;

        // ── Asteroids: normal rocks (under the planets) ────────────────────
        List<Asteroid> asteroids = snapshotAsteroids();
        int rockCount = 0, incomingCount = 0;

        for (Asteroid a : asteroids) {
            if (a.getKind() == Asteroid.Kind.INCOMING) { incomingCount++; continue; }
            rockCount++;

            double[] ap = worldToMinimap(a.getX(), a.getY(), mcx, mcy);
            double ax = Math.max(mx + 4, Math.min(mx + SIZE - 4, ap[0]));
            double ay = Math.max(my + 4, Math.min(my + SIZE - 4, ap[1]));

            Color rc = rockColor(a.getTint());
            gc.setFill(Color.color(rc.getRed(), rc.getGreen(), rc.getBlue(), 0.8));
            gc.fillOval(ax - 1, ay - 1, 2, 2);
        }

        // ── Planets (same procedural renderer as the big map) ──────────────
        for (Planet p : planets) {
            double[] mp = worldToMinimap(p.getX(), p.getY(), mcx, mcy);

            // Skip if outside bounds
            if (mp[0] < mx || mp[0] > mx + SIZE || mp[1] < my || mp[1] > my + SIZE) continue;

            double dotR = Math.max(5, Math.min(9, p.getRadius() * 0.2));

            // soft halo
            RadialGradient halo = new RadialGradient(0, 0, mp[0], mp[1], dotR + 5, false, CycleMethod.NO_CYCLE,
                    new Stop(0.5, Color.color(0.5, 0.8, 1, 0.15)),
                    new Stop(1.0, Color.color(0.5, 0.8, 1, 0)));
            gc.setFill(halo);
            gc.fillOval(mp[0] - dotR - 5, mp[1] - dotR - 5, (dotR + 5) * 2, (dotR + 5) * 2);

            proceduralRenderer.drawAt(gc, p.getName(), mp[0], mp[1], dotR, p.getColor());

            // drawAt may change font/alignment, so reset before label
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 8));
            gc.setTextAlign(TextAlignment.LEFT);

            double lx = mp[0] + dotR + 3;
            double ly = mp[1] + 3;
            gc.setFill(Color.color(0, 0, 0.05, 0.8));
            gc.fillText(p.getName(), lx + 0.8, ly + 0.8);
            gc.setFill(Color.color(1, 1, 1, 0.75));
            gc.fillText(p.getName(), lx, ly);
        }

        // ── Asteroids: incoming (on top of everything) ─────────────────────
        for (Asteroid a : asteroids) {
            if (a.getKind() != Asteroid.Kind.INCOMING) continue;

            double[] ap = worldToMinimap(a.getX(), a.getY(), mcx, mcy);
            double ax = Math.max(mx + 5, Math.min(mx + SIZE - 5, ap[0]));
            double ay = Math.max(my + 5, Math.min(my + SIZE - 5, ap[1]));

            double pulse = 0.5 + 0.5 * Math.sin(t * 6 + a.getTint() * 10);

            // dashed warning line to the ship
            gc.setStroke(Color.color(1, 0.3, 0.25, 0.25 + 0.2 * pulse));
            gc.setLineWidth(0.8);
            gc.setLineDashes(2, 3);
            gc.setLineDashOffset(-t * 15);
            gc.strokeLine(ax, ay, sx, sy);
            gc.setLineDashes((double[]) null);
            gc.setLineDashOffset(0);

            // red pulsing glow
            double gr = 6 + 3 * pulse;
            RadialGradient glow = new RadialGradient(0, 0, ax, ay, gr, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(1, 0.3, 0.25, 0.65)),
                    new Stop(1.0, Color.color(1, 0.3, 0.25, 0)));
            gc.setFill(glow);
            gc.fillOval(ax - gr, ay - gr, gr * 2, gr * 2);

            gc.setFill(Color.color(1, 0.55, 0.45, 0.98));
            gc.fillOval(ax - 2, ay - 2, 4, 4);
        }

        // ── Ship marker ────────────────────────────────────────────────────
        RadialGradient shipGlow = new RadialGradient(0, 0, sx, sy, 10, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.3, 1, 0.8, 0.45)),
                new Stop(1.0, Color.color(0.3, 1, 0.8, 0)));
        gc.setFill(shipGlow);
        gc.fillOval(sx - 10, sy - 10, 20, 20);

        gc.save();
        gc.translate(sx, sy);
        gc.rotate(Math.toDegrees(ship.getAngle()));
        gc.setFill(ACCENT);
        gc.fillPolygon(new double[]{6, -4, -4}, new double[]{0, -3, 3}, 3);
        gc.restore();

        // Pulsing ring indicator when ship is at edge
        if (offEdge) {
            double pulse = 0.5 + 0.5 * Math.sin(t * 4);
            gc.setStroke(Color.color(0.3, 1, 0.8, 0.6 + 0.4 * pulse));
            gc.setLineWidth(1.5);
            gc.strokeOval(sx - 6, sy - 6, 12, 12);
        }

        gc.restore(); // end clip

        // ── Labels below minimap ───────────────────────────────────────────
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New", 9));
        gc.setFill(Color.color(1, 1, 1, 0.35));
        gc.fillText("MINIMAP  ·  click to expand", mcx, my + SIZE + 14);

        double speed = ship.getSpeed();
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
        gc.setFill(Color.color(0.3, 1, 0.8, 0.75));
        gc.fillText(String.format("SPD %.2f", speed), mcx, my + SIZE + 27);

        // asteroid counts
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 9));
        if (incomingCount > 0) {
            double pulse = 0.5 + 0.5 * Math.sin(t * 6);
            gc.setFill(Color.color(1, 0.4, 0.35, 0.7 + 0.3 * pulse));
            gc.fillText("AST " + rockCount + "  ·  INCOMING " + incomingCount, mcx, my + SIZE + 40);
        } else {
            gc.setFill(Color.color(1, 1, 1, 0.4));
            gc.fillText("AST " + rockCount + "  ·  INCOMING 0", mcx, my + SIZE + 40);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // Log scale: converts world distance to minimap pixels
    // Inner planets spread out, outer planets compressed
    private double logScale(double worldDist) {
        if (worldDist <= 0) return 0;
        return (Math.log(worldDist) / Math.log(MAX_DIST)) * MAX_RADIUS;
    }

    // Converts any world (x, y) to minimap screen position using log scale
    private double[] worldToMinimap(double wx, double wy, double mcx, double mcy) {
        double dist = Math.sqrt(wx * wx + wy * wy);
        if (dist < 0.001) return new double[]{mcx, mcy};

        double scaledDist = logScale(dist);
        double mx = mcx + (wx / dist) * scaledDist;
        double my = mcy + (wy / dist) * scaledDist;
        return new double[]{mx, my};
    }

    public boolean isClickOnMinimap(double mouseX, double mouseY,
                                    double canvasW, double canvasH) {
        double mx = canvasW - SIZE - PADDING;
        double my = PADDING;
        return mouseX >= mx && mouseX <= mx + SIZE && mouseY >= my && mouseY <= my + SIZE;
    }
}