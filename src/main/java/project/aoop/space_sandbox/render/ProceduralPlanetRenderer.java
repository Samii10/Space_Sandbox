package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.Planet;
import project.aoop.space_sandbox.entity.Star;

import java.util.List;
import java.util.Random;
@Component
public class ProceduralPlanetRenderer {
    private static Color darken(Color c, double k) {
        return Color.color(c.getRed() * k, c.getGreen() * k, c.getBlue() * k, 1.0);
    }

    private static Color edge(Color base) {
        return darken(base, 0.38);
    }


    private static final double SATURN_TILT      = 0;
    private static final double URANUS_RING_TILT = 0;
    private static final double HAUMEA_TILT      = 0;

    // ── Kuiper belt data  ──
    private static final int    K_COUNT         = 3500;
    private static final long   K_SEED          = 7L;
    private static final double K_ANGULAR_SPEED = 0.02;
    private static final double K_DOT_LIMIT_PX  = 2.4;
    private static final Color  K_TINT          = Color.color(0.59, 0.71, 1.0, 0.05);

    private static final Color[][] K_PALETTE = {
            {Color.web("#d6deea"), Color.web("#98a6bd")},
            {Color.web("#e9e3d6"), Color.web("#aaa291")},
            {Color.web("#b9c6d9"), Color.web("#7f8fa8")},
            {Color.web("#f1f4f9"), Color.web("#a5b2c6")},
            {Color.web("#cdb8a6"), Color.web("#8e7a68")}
    };


    private static final String[] K_DWARF_NAME = {"Pluto", "Haumea", "Makemake", "Eris"};
    private static final double[] K_DWARF_F    = {0.475, 0.655, 0.790, 1.90};
    private static final double[] K_DWARF_A    = {0.6, 2.4, 4.0, 5.3};
    private static final double[] K_DWARF_VIS  = {0.9, 0.7, 0.7, 0.95};
    private static final double[][] K_MOON = {{1.9, 0.42, 0.9}, {1.6, 0.20, 1.3},
            {1.7, 0.16, 1.1}, {1.8, 0.20, 0.8}};
    private static final Color[] K_MOON_COLOR = {Color.web("#9d948c"), Color.web("#dfe6ee"),
            Color.web("#6b5a50"), Color.web("#aeb3c2")};

    private final double[] kAngle  = new double[K_COUNT];
    private final double[] kRadial = new double[K_COUNT];
    private final double[] kSize   = new double[K_COUNT];
    private final double[] kRot    = new double[K_COUNT];
    private final double[] kSpin   = new double[K_COUNT];
    private final int[]    kShape  = new int[K_COUNT];
    private final int[]    kPal    = new int[K_COUNT];
    private final double[][] kShapes = new double[6][8];
    private final double[] kvx = new double[8];
    private final double[] kvy = new double[8];

    private final GameCamera camera;

    public ProceduralPlanetRenderer(GameCamera camera) {
        this.camera = camera;
        initKuiperBelt();
    }

    // ── Used by game view (converts world → screen first) ──
    public void drawPlanet(GraphicsContext gc, Planet planet) {
        double sx = camera.toScreenX(planet.getX());
        double sy = camera.toScreenY(planet.getY());
        double r  = Math.max(planet.getRadius() * camera.getZoom(), 4);

        if (sx < -r*3 || sx > camera.getScreenW() + r*3) return;
        if (sy < -r*3 || sy > camera.getScreenH() + r*3) return;

        drawAt(gc, planet.getName(), sx, sy, r, planet.getColor());

        // Label when zoomed in enough
        if (camera.getZoom() > 0.05) {
            gc.setFill(Color.color(1, 1, 1, 0.7));
            gc.setFont(javafx.scene.text.Font.font("Courier New", 11));
            gc.fillText(planet.getName(), sx + r + 5, sy + 4);
        }
    }

    // ── Used by game view for Sun ──
    public void drawSun(GraphicsContext gc, Star star) {
        double sx = camera.toScreenX(star.getX());
        double sy = camera.toScreenY(star.getY());
        double r  = star.getRadius() * camera.getZoom();
        double lw = Math.max(1.5, r * 0.06);

        gc.save();

        // Soft glow
        for (int i = 4; i >= 1; i--) {
            gc.setFill(Color.color(1, 0.75, 0.15, 0.04 * i));
            gc.fillOval(sx - r*i*1.5, sy - r*i*1.5, r*i*3, r*i*3);
        }


        int n = 12;
        double[] xs = new double[3];
        double[] ys = new double[3];
        gc.setFill(Color.web("#ffb300"));
        gc.setStroke(Color.web("#b34700"));
        gc.setLineWidth(lw);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        for (int i = 0; i < n; i++) {
            double a   = i * 2 * Math.PI / n;
            double tip = r * (i % 2 == 0 ? 1.75 : 1.45);
            double hw  = Math.PI / n * 0.55;
            xs[0] = sx + Math.cos(a - hw) * r * 0.95;  ys[0] = sy + Math.sin(a - hw) * r * 0.95;
            xs[1] = sx + Math.cos(a) * tip;            ys[1] = sy + Math.sin(a) * tip;
            xs[2] = sx + Math.cos(a + hw) * r * 0.95;  ys[2] = sy + Math.sin(a + hw) * r * 0.95;
            gc.fillPolygon(xs, ys, 3);
            gc.strokePolygon(xs, ys, 3);
        }
        gc.setFill(Color.web("#ffb627"));
        gc.fillOval(sx - r, sy - r, r*2, r*2);
        gc.setFill(Color.web("#ffd93b"));
        gc.fillOval(sx - r*0.85 - r*0.10, sy - r*0.85 - r*0.10, r*1.7, r*1.7);
        gc.setFill(Color.web("#fff3a0"));
        gc.fillOval(sx - r*0.55 - r*0.18, sy - r*0.55 - r*0.18, r*1.1, r*1.1);

        // Outline
        gc.setStroke(Color.web("#b34700"));
        gc.setLineWidth(lw * 1.3);
        gc.strokeOval(sx - r, sy - r, r*2, r*2);

        // Highlight
        drawHighlight(gc, sx, sy, r);

        gc.restore();
    }

    // ── Used by BOTH game view (via drawPlanet) and map overlay ──
    // Takes explicit screen coordinates — no camera involved
    public void drawAt(GraphicsContext gc, String name,
                       double cx, double cy, double r, Color color) {
        gc.save();

        if (name.equals("Haumea")) {
            gc.translate(cx, cy);
            gc.rotate(-HAUMEA_TILT);
            gc.scale(1, 0.62);
            cx = 0;
            cy = 0;
        }
        if (name.equals("Saturn")) drawSaturnRings(gc, cx, cy, r);
        if (name.equals("Uranus") && r > 6) uranusRing(gc, cx, cy, r, 0);
        if (r > 8 && (name.equals("Earth") || name.equals("Venus"))) {
            Color atm = name.equals("Earth")
                    ? Color.color(0.4, 0.7, 1.0, 1.0)
                    : Color.color(1.0, 0.9, 0.6, 1.0);
            gc.setFill(Color.color(atm.getRed(), atm.getGreen(), atm.getBlue(), 0.12));
            gc.fillOval(cx - r*1.32, cy - r*1.32, r*2.64, r*2.64);
            gc.setFill(Color.color(atm.getRed(), atm.getGreen(), atm.getBlue(), 0.20));
            gc.fillOval(cx - r*1.15, cy - r*1.15, r*2.30, r*2.30);
        }

        Color base = realBase(name, color);
        drawSphere(gc, cx, cy, r, base);

        // Details only visible when planet is big enough to see them
        if (r > 6) {
            switch (name) {
                case "Earth"   -> drawEarth(gc, cx, cy, r);
                case "Moon"    -> drawMoon(gc, cx, cy, r);
                case "Mars"    -> drawMars(gc, cx, cy, r);
                case "Venus"   -> drawVenus(gc, cx, cy, r);
                case "Mercury" -> drawMercury(gc, cx, cy, r);
                case "Jupiter" -> drawJupiter(gc, cx, cy, r);
                case "Saturn"  -> drawSaturnBands(gc, cx, cy, r);
                case "Uranus"  -> drawUranus(gc, cx, cy, r);
                case "Neptune" -> drawNeptune(gc, cx, cy, r);
                case "Pluto"    -> drawPluto(gc, cx, cy, r);
                case "Eris"     -> drawEris(gc, cx, cy, r);
                case "Haumea"   -> drawHaumea(gc, cx, cy, r);
                case "Makemake" -> drawMakemake(gc, cx, cy, r);
            }
        }


        drawShade(gc, cx, cy, r, base);
        drawHighlight(gc, cx, cy, r);


        if (name.equals("Saturn")) drawSaturnRingsFront(gc, cx, cy, r);
        if (name.equals("Uranus") && r > 6) uranusRing(gc, cx, cy, r, 180);

        gc.restore();
    }


    private Color realBase(String name, Color fallback) {
        return switch (name) {
            case "Earth"   -> Color.web("#2f7fe0");
            case "Moon"    -> Color.web("#cfcfd8");
            case "Mars"    -> Color.web("#d2623b");
            case "Venus"   -> Color.web("#e9c47c");
            case "Mercury" -> Color.web("#9b958e");
            case "Jupiter" -> Color.web("#e8cba0");
            case "Saturn"  -> Color.web("#ecd9a0");
            case "Uranus"  -> Color.web("#a4e6e8");
            case "Neptune" -> Color.web("#3d63d6");
            case "Pluto"    -> Color.web("#d8bf9f");
            case "Eris"     -> Color.web("#e3e5ee");
            case "Haumea"   -> Color.web("#e8eff7");
            case "Makemake" -> Color.web("#c9855c");
            default        -> fallback;
        };
    }

    private void drawSphere(GraphicsContext gc, double cx, double cy,
                            double r, Color base) {
        gc.setFill(base);
        gc.fillOval(cx - r, cy - r, r*2, r*2);
    }


    private void drawShade(GraphicsContext gc, double cx, double cy, double r, Color base) {
        Color d = darken(base, 0.60);
        Color shade = Color.color(d.getRed(), d.getGreen(), d.getBlue(), 0.35);
        gc.setFill(new RadialGradient(
                0, 0, cx - r*0.22, cy - r*0.22, r * 1.32, false, CycleMethod.NO_CYCLE,
                new Stop(0.000, Color.TRANSPARENT),
                new Stop(0.780, Color.TRANSPARENT),
                new Stop(0.785, shade),
                new Stop(1.000, shade)));
        gc.fillOval(cx - r, cy - r, r*2, r*2);
    }


    private void drawHighlight(GraphicsContext gc, double cx, double cy, double r) {
        if (r < 5) return;
        gc.save();
        gc.setFill(Color.color(1, 1, 1, 0.55));
        gc.translate(cx - r*0.50, cy - r*0.55);
        gc.rotate(-40);
        gc.fillOval(-r*0.22, -r*0.11, r*0.44, r*0.22);
        gc.restore();
        gc.setFill(Color.color(1, 1, 1, 0.55));
        gc.fillOval(cx - r*0.22, cy - r*0.78, r*0.10, r*0.10);
    }



    private void clipCircle(GraphicsContext gc, double cx, double cy, double r) {
        gc.beginPath();
        gc.arc(cx, cy, r, r, 0, 360);
        gc.closePath();
        gc.clip();
    }


    private void cloud(GraphicsContext gc, double x, double y, double s) {
        gc.setFill(Color.color(1, 1, 1, 0.92));
        gc.fillOval(x - s,        y - s*0.5,  s*2.0, s);
        gc.fillOval(x - s*0.70,   y - s*0.95, s*1.0, s*1.0);
        gc.fillOval(x - s*0.05,   y - s*1.10, s*1.2, s*1.2);
    }


    private void crater(GraphicsContext gc, double x, double y, double rad,
                        Color dark, Color light) {
        gc.setFill(dark);
        gc.fillOval(x - rad, y - rad, rad*2, rad*2);
        gc.setFill(light);
        gc.fillOval(x - rad*0.75 + rad*0.15, y - rad*0.75 + rad*0.15, rad*1.5, rad*1.5);
    }


    private void blob(GraphicsContext gc, double cx, double cy, double r, double[] p) {
        int n = p.length / 2;
        gc.beginPath();
        gc.moveTo(cx + (p[2*(n-1)] + p[0]) / 2 * r, cy + (p[2*(n-1)+1] + p[1]) / 2 * r);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double px = p[2*i], py = p[2*i+1];
            double mx = (px + p[2*j]) / 2, my = (py + p[2*j+1]) / 2;
            gc.quadraticCurveTo(cx + px*r, cy + py*r, cx + mx*r, cy + my*r);
        }
        gc.closePath();
        gc.fill();
    }


    private void landBlob(GraphicsContext gc, double cx, double cy, double r,
                          double[] p, Color dark, Color light) {
        gc.setFill(dark);
        blob(gc, cx + r*0.035, cy + r*0.035, r, p);
        gc.setFill(light);
        blob(gc, cx, cy, r, p);
    }


    private void wavyBand(GraphicsContext gc, double cx, double cy, double r,
                          double y0, double y1, double amp, double phase, Color c) {
        int steps = 14;
        double[] xs = new double[(steps + 1) * 2];
        double[] ys = new double[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = cx - r + 2 * r * t;
            double w = Math.sin(t * Math.PI * 3 + phase) * amp * r;
            xs[i] = x;                 ys[i] = cy + y0 * r + w;
            xs[2*steps + 1 - i] = x;   ys[2*steps + 1 - i] = cy + y1 * r + w;
        }
        gc.setFill(c);
        gc.fillPolygon(xs, ys, xs.length);
    }

    // Crater with bright rim (Moon / Mercury)
    private void rimCrater(GraphicsContext gc, double x, double y, double rad,
                           Color rim, Color floor) {
        gc.setFill(floor);
        gc.fillOval(x - rad, y - rad, rad*2, rad*2);
        gc.setStroke(rim);
        gc.setLineWidth(Math.max(1, rad * 0.28));
        gc.strokeOval(x - rad, y - rad, rad*2, rad*2);
    }

    // ── Planet surface details ─────────────────────────────────────────────────

    private void drawEarth(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);
        gc.setFill(Color.web("#2468c9"));
        gc.fillOval(cx - r*0.95, cy + r*0.10, r*1.9, r*0.95);

        Color dark  = Color.web("#2e8f43");
        Color light = Color.web("#5fc25a");

        // North America
        landBlob(gc, cx, cy, r, new double[]{
                -0.66,-0.42, -0.45,-0.60, -0.18,-0.52, -0.16,-0.30,
                -0.32,-0.18, -0.28,-0.02, -0.44,-0.06, -0.60,-0.22}, dark, light);
        // South America
        landBlob(gc, cx, cy, r, new double[]{
                -0.34, 0.08, -0.12, 0.16, -0.08, 0.38,
                -0.22, 0.72, -0.36, 0.52, -0.40, 0.26}, dark, light);
        // Europe
        landBlob(gc, cx, cy, r, new double[]{
                0.12,-0.60,  0.36,-0.66,  0.52,-0.46,  0.30,-0.32,  0.10,-0.36}, dark, light);
        // Asia
        landBlob(gc, cx, cy, r, new double[]{
                0.46,-0.58,  0.80,-0.42,  0.86,-0.12,  0.62,-0.06,  0.50,-0.28}, dark, light);
        // Africa
        landBlob(gc, cx, cy, r, new double[]{
                0.10,-0.22,  0.38,-0.22,  0.52, 0.00,  0.40, 0.24,
                0.32, 0.54,  0.16, 0.44,  0.10, 0.16,  0.00,-0.04}, dark, light);
        gc.setFill(Color.web("#e3c46f"));
        blob(gc, cx, cy, r, new double[]{
                0.12,-0.16,  0.36,-0.18,  0.42,-0.04,  0.24,-0.02,  0.10,-0.06});
        // Australia
        landBlob(gc, cx, cy, r, new double[]{
                0.58, 0.40,  0.80, 0.36,  0.84, 0.54,  0.64, 0.62}, dark, Color.web("#d9a95a"));

        // Ice caps
        gc.setFill(Color.web("#f4faff"));
        gc.fillOval(cx - r*0.55, cy - r*1.15, r*1.10, r*0.42);
        gc.fillOval(cx - r*0.50, cy + r*0.80, r*1.00, r*0.40);

        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setStroke(Color.color(1, 1, 1, 0.85));
        gc.setLineWidth(Math.max(1.2, r * 0.07));
        gc.strokeArc(cx - r*0.70, cy - r*0.78, r*0.90, r*0.34, 20, 150, ArcType.OPEN);
        gc.strokeArc(cx + r*0.00, cy + r*0.30, r*0.85, r*0.30, 200, 140, ArcType.OPEN);
        cloud(gc, cx - r*0.10, cy - r*0.08, r*0.16);
        cloud(gc, cx + r*0.45, cy + r*0.62, r*0.15);

        gc.restore();
    }

    private void drawMoon(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);
        gc.setFill(Color.web("#9c9cae"));
        blob(gc, cx, cy, r, new double[]{
                -0.70,-0.20, -0.52,-0.48, -0.25,-0.50, -0.20,-0.22,
                -0.30, 0.10, -0.46, 0.38, -0.66, 0.22});
        blob(gc, cx, cy, r, new double[]{
                -0.10,-0.58,  0.12,-0.64,  0.22,-0.44,  0.06,-0.30, -0.12,-0.38});
        blob(gc, cx, cy, r, new double[]{
                0.22,-0.34,  0.40,-0.38,  0.46,-0.20,  0.30,-0.12,  0.20,-0.20});
        blob(gc, cx, cy, r, new double[]{
                0.18,-0.06,  0.44,-0.04,  0.52, 0.12,  0.34, 0.22,  0.16, 0.14});


        gc.setStroke(Color.color(1, 1, 1, 0.55));
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth(Math.max(1, r * 0.04));
        double tx = cx - r*0.05, ty = cy + r*0.55;
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7 + 0.3;
            gc.strokeLine(tx, ty, tx + Math.cos(a) * r*0.55, ty + Math.sin(a) * r*0.55);
        }

        // Craters
        Color rim   = Color.web("#e8e8ef");
        Color floor = Color.web("#a9a9ba");
        rimCrater(gc, tx, ty, r*0.10, rim, floor);
        rimCrater(gc, cx + r*0.50, cy - r*0.55, r*0.12, rim, floor);
        rimCrater(gc, cx - r*0.55, cy + r*0.55, r*0.14, rim, floor);
        rimCrater(gc, cx + r*0.58, cy + r*0.38, r*0.08, rim, floor);
        rimCrater(gc, cx - r*0.15, cy + r*0.15, r*0.07, rim, floor);

        gc.restore();
    }

    private void drawMars(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);
        gc.setFill(Color.web("#8e3c26"));
        blob(gc, cx, cy, r, new double[]{
                -0.10,-0.20,  0.12,-0.28,  0.26,-0.05,  0.14, 0.26,  0.00, 0.18, -0.14, 0.02});
        blob(gc, cx, cy, r, new double[]{
                -0.62, 0.18, -0.38, 0.08, -0.30, 0.30, -0.48, 0.46, -0.66, 0.40});
        // Hellas (light basin)
        gc.setFill(Color.web("#e9a27a"));
        gc.fillOval(cx + r*0.22, cy + r*0.34, r*0.40, r*0.30);

        // Polar ice caps
        gc.setFill(Color.web("#fff5ec"));
        gc.fillOval(cx - r*0.42, cy - r*1.12, r*0.84, r*0.44);
        gc.fillOval(cx - r*0.30, cy + r*0.86, r*0.60, r*0.30);

        // Olympus Mons
        crater(gc, cx - r*0.58, cy - r*0.28, r*0.12,
                Color.web("#a8482a"), Color.web("#e07a4f"));

        gc.restore();
    }

    private void drawVenus(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);


        gc.setLineCap(StrokeLineCap.ROUND);
        double lw = Math.max(1.5, r * 0.10);
        for (int i = -2; i <= 2; i++) {
            double y = cy + i * r * 0.36;
            gc.setStroke(i % 2 == 0 ? Color.web("#f8e3a6") : Color.web("#d3a05a"));
            gc.setLineWidth(lw);
            gc.strokeArc(cx - r*0.95, y - r*0.20, r*1.9, r*0.40, 200, 140, ArcType.OPEN);
        }

        gc.setStroke(Color.color(1, 0.96, 0.8, 0.7));
        gc.setLineWidth(Math.max(1, r * 0.06));
        gc.strokeArc(cx - r*0.55, cy - r*0.55, r*0.80, r*0.80, 100, 120, ArcType.OPEN);
        gc.strokeArc(cx - r*0.10, cy - r*0.10, r*0.80, r*0.60, 280, 110, ArcType.OPEN);

        gc.restore();
    }

    private void drawMercury(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);


        gc.setFill(Color.web("#857f78"));
        gc.fillOval(cx + r*0.05, cy - r*0.50, r*0.60, r*0.50);

        // Bright ray crater er ray
        gc.setStroke(Color.color(1, 1, 1, 0.40));
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth(Math.max(1, r * 0.04));
        double kx = cx - r*0.30, ky = cy + r*0.30;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + 0.2;
            gc.strokeLine(kx, ky, kx + Math.cos(a) * r*0.50, ky + Math.sin(a) * r*0.50);
        }

        Color rim   = Color.web("#c9c3bb");
        Color floor = Color.web("#6f6a64");
        double[][] craters = {
                {0.35, -0.30, 0.18}, {-0.40, -0.10, 0.15}, {0.10, 0.45, 0.14},
                {-0.20, -0.50, 0.10}, {0.50, 0.30, 0.12}, {-0.05, 0.05, 0.09},
                {0.62, -0.05, 0.07}
        };
        for (double[] c : craters) {
            rimCrater(gc, cx + c[0]*r, cy + c[1]*r, c[2]*r, rim, floor);
        }
        rimCrater(gc, kx, ky, r*0.09, Color.web("#f2efe9"), Color.web("#8a847d"));

        gc.restore();
    }

    private void drawJupiter(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);


        Color cream = Color.web("#f3e3c0");
        Color tan   = Color.web("#d9a566");
        Color brown = Color.web("#b5683a");
        Color rust  = Color.web("#c98450");

        wavyBand(gc, cx, cy, r, -1.10, -0.78, 0.02, 0.0, tan);
        wavyBand(gc, cx, cy, r, -0.78, -0.55, 0.03, 1.0, cream);
        wavyBand(gc, cx, cy, r, -0.55, -0.32, 0.03, 2.0, brown);
        wavyBand(gc, cx, cy, r, -0.32, -0.12, 0.03, 0.5, cream);
        wavyBand(gc, cx, cy, r, -0.12,  0.10, 0.03, 1.5, rust);
        wavyBand(gc, cx, cy, r,  0.10,  0.34, 0.03, 2.5, cream);
        wavyBand(gc, cx, cy, r,  0.34,  0.58, 0.03, 0.8, brown);
        wavyBand(gc, cx, cy, r,  0.58,  0.82, 0.03, 1.8, cream);
        wavyBand(gc, cx, cy, r,  0.82,  1.10, 0.02, 0.3, tan);


        double gx = cx + r*0.05, gy = cy + r*0.34, gw = r*0.52, gh = r*0.30;
        gc.setFill(cream);
        gc.fillOval(gx - gw*0.12, gy - gh*0.20, gw*1.24, gh*1.40);
        gc.setFill(Color.web("#d9503a"));
        gc.fillOval(gx, gy, gw, gh);
        gc.setFill(Color.web("#f08a6a"));
        gc.fillOval(gx + gw*0.24, gy + gh*0.24, gw*0.52, gh*0.52);
        gc.setStroke(Color.color(0.45, 0.1, 0.05, 0.75));
        gc.setLineWidth(Math.max(1, r * 0.03));
        gc.strokeOval(gx, gy, gw, gh);


        gc.setFill(Color.color(1, 1, 1, 0.85));
        gc.fillOval(cx - r*0.55, cy + r*0.40, r*0.16, r*0.10);
        gc.fillOval(cx - r*0.35, cy - r*0.40, r*0.12, r*0.08);

        gc.restore();
    }
    private void drawSaturnRings(GraphicsContext gc, double cx, double cy, double r) {
        saturnRing(gc, cx, cy, r, 0);
    }

    private void drawSaturnRingsFront(GraphicsContext gc, double cx, double cy, double r) {
        saturnRing(gc, cx, cy, r, 180);
    }


    private void saturnRing(GraphicsContext gc, double cx, double cy, double r, double start) {
        boolean front = start >= 180;
        double k = 0.24;
        Color line = edge(Color.web("#ecd9a0"));

        gc.save();
        gc.translate(cx, cy);
        gc.rotate(-SATURN_TILT);

        //          inner  outer
        ringBand(gc, r, k, 1.50, 1.66, front, Color.web("#d8b36a"));   // inner ring
        ringBand(gc, r, k, 1.73, 1.99, front, Color.web("#f1d594"));   // B ring
        ringBand(gc, r, k, 2.03, 2.20, front, Color.web("#e3bd78"));   // A ring

        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setStroke(line);
        gc.setLineWidth(Math.max(0.8, r * 0.012));
        for (double q : new double[]{1.50, 2.20}) {
            gc.strokeArc(-r*q, -r*q*k, r*q*2, r*q*k*2, start, 180, ArcType.OPEN);
        }

        gc.setStroke(Color.color(line.getRed(), line.getGreen(), line.getBlue(), 0.55));
        gc.setLineWidth(Math.max(0.8, r * 0.008));
        for (double q : new double[]{1.86, 2.11}) {
            gc.strokeArc(-r*q, -r*q*k, r*q*2, r*q*k*2, start, 180, ArcType.OPEN);
        }

        gc.restore();
    }


    private void ringBand(GraphicsContext gc, double r, double k,
                          double in, double out, boolean front, Color c) {
        int n = 36;
        double[] xs = new double[(n + 1) * 2];
        double[] ys = new double[(n + 1) * 2];
        double sign = front ? 1 : -1;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * i / n;
            double ca = Math.cos(a), sa = Math.sin(a);
            xs[i] = ca * r * out;               ys[i] = sign * sa * r * out * k;
            xs[2*n + 1 - i] = ca * r * in;      ys[2*n + 1 - i] = sign * sa * r * in * k;
        }
        gc.setFill(c);
        gc.fillPolygon(xs, ys, xs.length);
    }

    private void drawSaturnBands(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);


        gc.translate(cx, cy);
        gc.rotate(-SATURN_TILT);

        Color a = Color.web("#f4e7b8");
        Color b = Color.web("#d9a560");
        Color c = Color.web("#e8c27a");
        double[] edges = {-1.10, -0.72, -0.46, -0.18, 0.10, 0.36, 0.62, 0.86, 1.10};
        Color[] cols   = {a, c, b, a, c, b, a, c};
        for (int i = 0; i < cols.length; i++) {
            wavyBand(gc, 0, 0, r, edges[i], edges[i + 1], 0, 0, cols[i]);
        }

        gc.setStroke(Color.color(0.30, 0.18, 0.06, 0.55));
        gc.setLineWidth(Math.max(0.8, r * 0.010));
        for (int i = 1; i < edges.length - 1; i++) {
            gc.strokeLine(-r, edges[i] * r, r, edges[i] * r);
        }
        gc.setFill(Color.web("#b88a4a"));
        double hy = -r * 0.88, hr = r * 0.16;
        double[] xs = new double[6], ys = new double[6];
        for (int i = 0; i < 6; i++) {
            xs[i] = Math.cos(i * Math.PI / 3) * hr * 1.6;
            ys[i] = hy + Math.sin(i * Math.PI / 3) * hr * 0.6;
        }
        gc.fillPolygon(xs, ys, 6);
        gc.setFill(Color.color(0.15, 0.08, 0.05, 0.25));
        gc.fillRect(-r, r * 0.20, r * 2, r * 0.14);

        gc.restore();
    }


    private void uranusRing(GraphicsContext gc, double cx, double cy, double r, double start) {
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(-URANUS_RING_TILT);
        gc.setLineCap(StrokeLineCap.BUTT);
        double rx = r * 1.55, ry = r * 0.42;
        gc.setStroke(edge(Color.web("#a4e6e8")));
        gc.setLineWidth(Math.max(1.5, r * 0.075));
        gc.strokeArc(-rx, -ry, rx*2, ry*2, start, 180, ArcType.OPEN);
        gc.setStroke(Color.web("#dff7fa"));
        gc.setLineWidth(Math.max(1, r * 0.06));
        gc.strokeArc(-rx, -ry, rx*2, ry*2, start, 180, ArcType.OPEN);
        gc.restore();
    }

    private void drawUranus(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);
        gc.setFill(Color.web("#c8f4f4"));
        gc.fillOval(cx - r*0.80, cy - r*1.25, r*1.60, r*0.85);
        gc.setFill(Color.web("#8fd8de"));
        gc.fillOval(cx - r*1.05, cy + r*0.25, r*2.10, r*0.40);
        gc.setFill(Color.web("#b6eef0"));
        gc.fillOval(cx - r*1.05, cy - r*0.10, r*2.10, r*0.32);
        gc.setFill(Color.color(1, 1, 1, 0.8));
        gc.fillOval(cx + r*0.30, cy - r*0.45, r*0.10, r*0.10);
        gc.fillOval(cx + r*0.42, cy - r*0.32, r*0.06, r*0.06);

        gc.restore();
    }

    private void drawNeptune(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);
        wavyBand(gc, cx, cy, r, -0.55, -0.30, 0.03, 0.5, Color.web("#2f4fb8"));
        wavyBand(gc, cx, cy, r,  0.45,  0.75, 0.03, 1.8, Color.web("#2f4fb8"));
        wavyBand(gc, cx, cy, r, -1.10, -0.80, 0.02, 0.0, Color.web("#5d86f0"));
        double sx = cx - r*0.35, sy = cy - r*0.05, sw = r*0.52, sh = r*0.30;
        gc.setFill(Color.web("#1c2f8e"));
        gc.fillOval(sx, sy, sw, sh);
        gc.setStroke(Color.color(1, 1, 1, 0.55));
        gc.setLineWidth(Math.max(1, r * 0.03));
        gc.strokeOval(sx, sy, sw, sh);

        // White cirrus cloud streaks
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setStroke(Color.color(0.92, 0.97, 1.0, 0.9));
        gc.setLineWidth(Math.max(1.5, r * 0.07));
        gc.strokeArc(cx - r*0.05, cy - r*0.05, r*0.90, r*0.26, 15, 140, ArcType.OPEN);
        gc.strokeArc(cx - r*0.70, cy + r*0.38, r*1.00, r*0.28, 200, 130, ArcType.OPEN);
        gc.strokeArc(cx + r*0.05, cy - r*0.55, r*0.60, r*0.20, 200, 120, ArcType.OPEN);

        gc.restore();
    }

    // ── Kuiper belt ────────────────────────────────────────────────────────────

    private void initKuiperBelt() {
        Random rnd = new Random(K_SEED);

        for (double[] sh : kShapes) {
            for (int i = 0; i < sh.length; i++) sh[i] = 0.68 + rnd.nextDouble() * 0.32;
        }

        for (int i = 0; i < K_COUNT; i++) {
            double u = rnd.nextDouble();
            double t;
            if (u < 0.22) {
                t = 0.47 + (rnd.nextDouble() + rnd.nextDouble() - 1) * 0.08;   // 3:2 resonance jhnk (Pluto)
            } else if (u < 0.34) {
                t = 0.89 + (rnd.nextDouble() + rnd.nextDouble() - 1) * 0.09;   // 2:1 resonance jhnk
            } else {
                t = (rnd.nextDouble() + rnd.nextDouble()) / 2 * (0.8 + rnd.nextDouble() * 0.4);
            }
            kRadial[i] = Math.max(0, Math.min(1, t));
            kAngle[i]  = rnd.nextDouble() * Math.PI * 2;
            kSize[i]   = (0.07 + Math.pow(rnd.nextDouble(), 2.4) * 0.45) / 20.0;
            kRot[i]    = rnd.nextDouble() * Math.PI * 2;
            kSpin[i]   = (rnd.nextDouble() - 0.5) * 0.8;
            kShape[i]  = i % kShapes.length;
            kPal[i]    = rnd.nextInt(K_PALETTE.length);
        }
    }


    public void drawKuiperBelt(GraphicsContext gc, Star star, List<Planet> planets, double time) {
        double a = planets.stream().mapToDouble(Planet::getSemiMajorAxis).max().orElse(0);
        if (a <= 0) return;
        drawKuiperBelt(gc, star.getX(), star.getY(), a * 1.05, a * 1.70, time);
    }


    public void drawKuiperBeltMap(GraphicsContext gc, double cx, double cy,
                                  double innerPx, double outerPx, double time) {
        double width = outerPx - innerPx;
        gc.save();
        for (int i = 0; i < K_COUNT; i++) {
            double d = innerPx + kRadial[i] * width;
            double a = kAngle[i] + K_ANGULAR_SPEED * Math.pow(innerPx / d, 1.5) * time;
            double dot = kSize[i] > 0.012 ? 2.0 : 1.2;
            gc.setFill(K_PALETTE[kPal[i]][0]);
            gc.fillRect(cx + Math.cos(a) * d, cy + Math.sin(a) * d, dot, dot);
        }


        for (int i = 0; i < K_DWARF_NAME.length; i++) {
            double d = innerPx + K_DWARF_F[i] * width;
            double a = K_DWARF_A[i] + K_ANGULAR_SPEED * Math.pow(innerPx / d, 1.5) * time;
            double x = cx + Math.cos(a) * d;
            double y = cy + Math.sin(a) * d;
            double r = 7;

            drawAt(gc, K_DWARF_NAME[i], x, y, r, Color.GRAY);

            gc.setFill(Color.color(1, 1, 1, 0.75));
            gc.setFont(javafx.scene.text.Font.font("Courier New", 10));
            gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
            gc.fillText(K_DWARF_NAME[i], x + r + 4, y + 3);
        }

        gc.restore();
    }


    public void drawKuiperBelt(GraphicsContext gc, double sunX, double sunY,
                               double innerR, double outerR, double time) {
        double zoom  = camera.getZoom();
        double width = outerR - innerR;
        double cx    = camera.toScreenX(sunX);
        double cy    = camera.toScreenY(sunY);
        double sw    = camera.getScreenW();
        double sh    = camera.getScreenH();

        gc.save();


        double rMid = (innerR + outerR) / 2 * zoom;
        gc.setStroke(K_TINT);
        gc.setLineWidth(width * zoom);
        gc.strokeOval(cx - rMid, cy - rMid, rMid * 2, rMid * 2);

        for (int i = 0; i < K_COUNT; i++) {
            double d = innerR + kRadial[i] * width;

            double a = kAngle[i] + K_ANGULAR_SPEED * Math.pow(innerR / d, 1.5) * time;

            double sx = camera.toScreenX(sunX + Math.cos(a) * d);
            double sy = camera.toScreenY(sunY + Math.sin(a) * d);
            double px = kSize[i] * width * zoom;


            if (sx < -40 || sx > sw + 40 || sy < -40 || sy > sh + 40) continue;

            Color[] c = K_PALETTE[kPal[i]];
            if (px < K_DOT_LIMIT_PX) {
                double dot = Math.max(1, px * 0.8);
                gc.setFill(c[0]);
                gc.fillRect(sx, sy, dot, dot);
            } else {
                drawKuiperRock(gc, sx, sy, px, kRot[i] + time * kSpin[i], kShapes[kShape[i]], c);
            }
        }


        drawKuiperDwarfs(gc, sunX, sunY, innerR, width, zoom, time);

        gc.restore();
    }

    private void drawKuiperDwarfs(GraphicsContext gc, double sunX, double sunY,
                                  double innerR, double width, double zoom, double time) {
        double sw = camera.getScreenW();
        double sh = camera.getScreenH();

        for (int i = 0; i < K_DWARF_NAME.length; i++) {
            double d = innerR + K_DWARF_F[i] * width;
            double a = K_DWARF_A[i] + K_ANGULAR_SPEED * Math.pow(innerR / d, 1.5) * time;
            double x = camera.toScreenX(sunX + Math.cos(a) * d);
            double y = camera.toScreenY(sunY + Math.sin(a) * d);
            double r = Math.max(K_DWARF_VIS[i] / 20.0 * width * zoom, 4.5);

            if (x < -r*3 || x > sw + r*3 || y < -r*3 || y > sh + r*3) continue;

            drawAt(gc, K_DWARF_NAME[i], x, y, r, Color.GRAY);


            if (r > 14) {
                double ma = K_DWARF_A[i] + time * K_MOON[i][2];
                double mr = Math.max(r * K_MOON[i][1], 2);
                gc.setFill(K_MOON_COLOR[i]);
                gc.fillOval(x + Math.cos(ma) * r * K_MOON[i][0] - mr,
                        y + Math.sin(ma) * r * K_MOON[i][0] * 0.6 - mr, mr*2, mr*2);
            }

            gc.setFill(Color.color(1, 1, 1, 0.75));
            gc.setFont(javafx.scene.text.Font.font("Courier New", 12));
            gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
            gc.fillText(K_DWARF_NAME[i], x + r + 6, y + 4);
        }
    }

    private void drawKuiperRock(GraphicsContext gc, double x, double y, double px,
                                double rotation, double[] sh, Color[] c) {
        gc.setFill(c[1]);
        kuiperPolygon(gc, x + px * 0.10, y + px * 0.10, px, rotation, sh);
        gc.setFill(c[0]);
        kuiperPolygon(gc, x, y, px * 0.92, rotation, sh);

        if (px > 8) {
            gc.setFill(Color.color(1, 1, 1, 0.55));
            gc.fillOval(x - px * 0.50, y - px * 0.45, px * 0.40, px * 0.20);
            gc.setFill(c[1]);
            gc.fillOval(x + px * 0.11, y - px * 0.04, px * 0.28, px * 0.28);
        }
    }

    private void kuiperPolygon(GraphicsContext gc, double x, double y, double radius,
                               double rotation, double[] sh) {
        for (int i = 0; i < 8; i++) {
            double a = rotation + i * Math.PI / 4;
            kvx[i] = x + Math.cos(a) * radius * sh[i];
            kvy[i] = y + Math.sin(a) * radius * sh[i];
        }
        gc.fillPolygon(kvx, kvy, 8);
    }


    private void heart(GraphicsContext gc, double cx, double cy, double s) {
        gc.beginPath();
        gc.moveTo(cx, cy + s*0.28);
        gc.bezierCurveTo(cx - s*0.50, cy - s*0.05, cx - s*0.28, cy - s*0.45, cx, cy - s*0.20);
        gc.bezierCurveTo(cx + s*0.28, cy - s*0.45, cx + s*0.50, cy - s*0.05, cx, cy + s*0.28);
        gc.closePath();
        gc.fill();
    }

    private void drawPluto(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);


        gc.setFill(Color.web("#8d5f48"));
        gc.fillOval(cx - r*0.85, cy + r*0.07, r*0.80, r*0.56);

        gc.setFill(Color.web("#f6ead6"));
        heart(gc, cx, cy, r);

        gc.setFill(Color.web("#b9926f"));
        gc.fillOval(cx + r*0.15, cy - r*0.58, r*0.50, r*0.36);

        gc.restore();
    }

    private void drawEris(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);


        gc.setFill(Color.web("#c4c8d6"));
        gc.fillOval(cx - r*0.75, cy - r*0.10, r*0.90, r*0.60);
        gc.fillOval(cx + r*0.10, cy - r*0.55, r*0.60, r*0.40);

        gc.restore();
    }

    // Haumea:
    private void drawHaumea(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);

        gc.setFill(Color.web("#c9d8ea"));
        gc.fillOval(cx - r*0.75, cy - r*0.35, r*0.60, r*0.40);

        gc.setFill(Color.web("#b5543f"));
        gc.fillOval(cx, cy - r*0.09, r*0.40, r*0.28);

        gc.restore();
    }

    private void drawMakemake(GraphicsContext gc, double cx, double cy, double r) {
        gc.save();
        clipCircle(gc, cx, cy, r);

        gc.setFill(Color.web("#a8613f"));
        gc.fillOval(cx - r*0.80, cy, r*1.00, r*0.60);
        gc.setFill(Color.web("#e0a17a"));
        gc.fillOval(cx + r*0.05, cy - r*0.50, r*0.60, r*0.40);

        gc.restore();
    }
}