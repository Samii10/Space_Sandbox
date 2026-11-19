package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.EnemyKind;
import project.aoop.space_sandbox.entity.EnemyShip;
import project.aoop.space_sandbox.entity.EnemyShip.LaserState;
import project.aoop.space_sandbox.service.EnemyService;

/**
 * Four enemy designs, all black with red, all with their cannon tip at (0, -11):
 *   GRUNT       sleek dagger with swept-back blades and forward fangs
 *   WARDEN      wide armored gunship: slab plates, shoulder cannons, amber trim
 *   DREADNOUGHT long forked battlecruiser: trident prongs, swept fins, glowing core
 *   ANNIHILATOR demonic crown: hooked horns, four blades, pulsing reactor, orbiting shards
 * Also draws enemy laser telegraphs / beams (up to 3 at once) and the boss health bar.
 */
@Component
public class EnemyRenderer {

    private record Pal(Color red, Color hot, Color dark) {}

    private static final Pal P_GRUNT = new Pal(Color.web("#d3121c"), Color.web("#ff4a3a"), Color.web("#5e0a10"));
    private static final Pal P_LOW   = new Pal(Color.web("#e0201b"), Color.web("#ffb347"), Color.web("#6b1a08"));
    private static final Pal P_MID   = new Pal(Color.web("#b3122e"), Color.web("#ff7a8a"), Color.web("#4a0614"));
    private static final Pal P_FINAL = new Pal(Color.web("#ff1f1f"), Color.web("#fff0e0"), Color.web("#8a0000"));

    private static final Color OUTLINE = Color.web("#000000");
    private static final Color DEEP    = Color.web("#07070a");
    private static final Color HULL    = Color.web("#17171f");
    private static final Color PLATE   = Color.web("#2b2b38");
    private static final Color STEEL   = Color.web("#4a4a5a");

    private final GameCamera   camera;
    private final EnemyService enemyService;

    public EnemyRenderer(GameCamera camera, EnemyService enemyService) {
        this.camera = camera;
        this.enemyService = enemyService;
    }

    private static Pal palFor(EnemyKind k) {
        return switch (k) {
            case GRUNT      -> P_GRUNT;
            case BOSS_LOW   -> P_LOW;
            case BOSS_MID   -> P_MID;
            case BOSS_FINAL -> P_FINAL;
        };
    }

    public void render(GraphicsContext gc) {
        double t    = System.nanoTime() * 1e-9;
        double zoom = camera.getZoom();
        EnemyShip boss = null;

        for (EnemyShip e : enemyService.getEnemies()) {
            if (e.getKind().isBoss()) boss = e;

            double sx = camera.toScreenX(e.getX());
            double sy = camera.toScreenY(e.getY());
            double margin = 120 + e.getKind().reach * zoom;   // long enough for a beam
            if (sx < -margin || sx > camera.getScreenW() + margin) continue;
            if (sy < -margin || sy > camera.getScreenH() + margin) continue;

            drawShip(gc, e, sx, sy, e.getUnitScale() * zoom, t);
            if (!e.getKind().isBoss()) drawHealthBar(gc, e, sx, sy, zoom);
        }

        if (boss != null) drawBossBar(gc, boss);
    }

    // ── ship dispatcher ──────────────────────────────────────────────────

    private void drawShip(GraphicsContext gc, EnemyShip e, double sx, double sy,
                          double unit, double t) {
        Pal p = palFor(e.getKind());

        gc.save();
        gc.translate(sx, sy);
        gc.rotate(Math.toDegrees(e.getAngle()) + 90.0);   // nose (-y) -> heading
        gc.scale(unit, unit);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setLineCap(StrokeLineCap.ROUND);

        drawLaser(gc, e, p, t);                            // beams under the ship

        switch (e.getKind()) {
            case GRUNT      -> drawGrunt(gc, e, p, t);
            case BOSS_LOW   -> drawWarden(gc, e, p, t);
            case BOSS_MID   -> drawDreadnought(gc, e, p, t);
            case BOSS_FINAL -> drawAnnihilator(gc, e, p, t);
        }

        gc.restore();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  GRUNT: dagger with swept blades
    // ═════════════════════════════════════════════════════════════════════

    private void drawGrunt(GraphicsContext gc, EnemyShip e, Pal p, double t) {
        if (e.isThrusting()) flame(gc, p, 0, 6.8, 1.0, flicker(t, 4.6, 1.3));
        for (int s = -1; s <= 1; s += 2) gruntWing(gc, p, s);

        barrel(gc, 0, -11.0, 0.9, 3.6);                         // nose cannon
        gc.setFill(p.red());
        gc.fillRoundRect(-0.55, -9.9, 1.1, 0.3, 0.12, 0.12);
        muzzleFlash(gc, p, e.getMuzzleFlash(), 1.0);

        gruntHullPath(gc);
        gc.setFill(hGrad(DEEP, PLATE, DEEP));
        gc.fill();
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.18);
        gc.stroke();

        gc.setStroke(tint(p.red(), 0.9));                       // red spine
        gc.setLineWidth(0.3);
        gc.strokeLine(0, -8.4, 0, -5.8);

        gc.setStroke(p.red());                                  // chevrons
        gc.setLineWidth(0.28);
        for (int i = 0; i < 2; i++) {
            double y = 0.4 + i * 1.5;
            gc.beginPath();
            gc.moveTo(-1.1, y);
            gc.lineTo(0, y - 1.0);
            gc.lineTo(1.1, y);
            gc.stroke();
        }

        gc.setFill(Color.web("#050507"));                       // engine mouth + ring
        gc.fillOval(-0.9, 6.2, 1.8, 0.8);
        gc.setStroke(e.isThrusting() ? p.hot() : p.dark());
        gc.setLineWidth(0.3);
        gc.strokeOval(-1.05, 6.05, 2.1, 1.0);

        // narrow glowing "eye"
        gc.setFill(new RadialGradient(-135, 0.4, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#ffe0d8")), new Stop(0.3, p.hot()),
                new Stop(0.75, p.red()), new Stop(1.0, p.dark())));
        gc.fillOval(-0.95, -5.3, 1.9, 3.4);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.14);
        gc.strokeOval(-0.95, -5.3, 1.9, 3.4);
        gc.setFill(Color.color(1, 1, 1, 0.35));
        gc.fillOval(-0.5, -4.9, 0.55, 0.9);

        if (e.getHitFlash() > 0.02) {
            flashColor(gc, e.getHitFlash());
            for (int s = -1; s <= 1; s += 2) { gruntWingPath(gc, s); gc.fill(); }
            gruntHullPath(gc);
            gc.fill();
        }
    }

    private void gruntWingPath(GraphicsContext gc, double s) {
        gc.beginPath();
        gc.moveTo(s * 1.3, -2.5);
        gc.lineTo(s * 5.2, -1.4);
        gc.lineTo(s * 10.0, 6.8);
        gc.lineTo(s * 6.4, 4.8);
        gc.lineTo(s * 4.4, 7.2);
        gc.lineTo(s * 1.4, 4.6);
        gc.closePath();
    }

    private void gruntFangPath(GraphicsContext gc, double s) {
        gc.beginPath();
        gc.moveTo(s * 2.8, -2.0);
        gc.lineTo(s * 5.9, -7.4);
        gc.lineTo(s * 5.0, -1.5);
        gc.closePath();
    }

    private void gruntWing(GraphicsContext gc, Pal p, int s) {
        gruntWingPath(gc, s);
        gc.setFill(hGradDir(s, PLATE, HULL, DEEP));
        gc.fill();
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.18);
        gc.stroke();

        gc.setStroke(p.red());
        gc.setLineWidth(0.3);
        gc.strokeLine(s * 5.2, -1.4, s * 10.0, 6.8);
        gc.setStroke(p.dark());
        gc.setLineWidth(0.22);
        gc.strokeLine(s * 6.4, 4.8, s * 4.4, 7.2);

        gruntFangPath(gc, s);
        gc.setFill(DEEP);
        gc.fill();
        gc.setStroke(p.red());
        gc.setLineWidth(0.16);
        gc.stroke();
        gc.setFill(p.hot());
        gc.fillOval(s * 5.9 - 0.25, -7.4 - 0.25, 0.5, 0.5);
    }

    private void gruntHullPath(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(0, -9.2);
        gc.lineTo(1.1, -6.0);
        gc.lineTo(1.7, -1.0);
        gc.lineTo(1.5, 4.0);
        gc.lineTo(1.0, 6.6);
        gc.lineTo(-1.0, 6.6);
        gc.lineTo(-1.5, 4.0);
        gc.lineTo(-1.7, -1.0);
        gc.lineTo(-1.1, -6.0);
        gc.closePath();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  WARDEN: wide armored gunship (amber trim)
    // ═════════════════════════════════════════════════════════════════════

    private void wardenHullPath(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(0, -9.0);
        gc.lineTo(3.2, -7.5);
        gc.lineTo(5.0, -3.0);
        gc.lineTo(5.6, 3.0);
        gc.lineTo(4.5, 6.8);
        gc.lineTo(-4.5, 6.8);
        gc.lineTo(-5.6, 3.0);
        gc.lineTo(-5.0, -3.0);
        gc.lineTo(-3.2, -7.5);
        gc.closePath();
    }

    private void wardenSlabPath(GraphicsContext gc, double s) {
        gc.beginPath();
        gc.moveTo(s * 5.0, -4.5);
        gc.lineTo(s * 9.2, -3.2);
        gc.lineTo(s * 9.9, 2.0);
        gc.lineTo(s * 9.0, 5.8);
        gc.lineTo(s * 5.4, 5.2);
        gc.closePath();
    }

    private void drawWarden(GraphicsContext gc, EnemyShip e, Pal p, double t) {
        boolean th = e.isThrusting();
        if (th) {
            double len = flicker(t, 5.4, 1.4);
            flame(gc, p, -3.4, 8.3, 1.3, len);
            flame(gc, p,  3.4, 8.3, 1.3, len);
        }

        // twin engine blocks
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * 3.4;
            gc.setFill(hGrad(DEEP, PLATE, DEEP));
            gc.fillRoundRect(cx - 1.5, 5.0, 3.0, 3.4, 0.8, 0.8);
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.15);
            gc.strokeRoundRect(cx - 1.5, 5.0, 3.0, 3.4, 0.8, 0.8);
            gc.setFill(Color.web("#050507"));
            gc.fillOval(cx - 0.95, 7.9, 1.9, 0.7);
            gc.setStroke(th ? p.hot() : p.dark());
            gc.setLineWidth(0.3);
            gc.strokeOval(cx - 1.1, 7.75, 2.2, 1.0);
        }

        // heavy armor slabs with amber trim and vents
        for (int s = -1; s <= 1; s += 2) {
            wardenSlabPath(gc, s);
            gc.setFill(hGradDir(s, PLATE, HULL, DEEP));
            gc.fill();
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.2);
            gc.stroke();

            gc.setStroke(p.red());
            gc.setLineWidth(0.4);
            gc.strokeLine(s * 9.2, -3.2, s * 9.9, 2.0);
            gc.strokeLine(s * 9.9, 2.0, s * 9.0, 5.8);

            gc.setStroke(DEEP);
            gc.setLineWidth(0.4);
            for (int i = 0; i < 3; i++) {
                gc.strokeLine(s * 6.4, -1.6 + i * 1.9, s * 8.6, -1.2 + i * 1.9);
            }

            // shoulder cannon (looks the part; shots leave from the nose)
            barrel(gc, s * 7.4, -8.6, 1.0, 5.6);
            gc.setFill(p.red());
            gc.fillRoundRect(s * 7.4 - 0.6, -6.6, 1.2, 0.3, 0.1, 0.1);
            glowDot(gc, s * 7.4, -8.7, 0.6, p.hot(), 0.8);
        }

        // hull
        wardenHullPath(gc);
        gc.setFill(hGrad(DEEP, PLATE, DEEP));
        gc.fill();
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.2);
        gc.stroke();

        // seams
        gc.setStroke(p.dark());
        gc.setLineWidth(0.3);
        gc.strokeLine(-2.3, -3.0, -2.3, 6.4);
        gc.strokeLine( 2.3, -3.0,  2.3, 6.4);

        // front visor plate with amber bars
        gc.beginPath();
        gc.moveTo(-2.8, -6.9);
        gc.lineTo(2.8, -6.9);
        gc.lineTo(3.7, -4.4);
        gc.lineTo(-3.7, -4.4);
        gc.closePath();
        gc.setFill(DEEP);
        gc.fill();
        gc.setStroke(p.red());
        gc.setLineWidth(0.16);
        gc.stroke();
        gc.setLineWidth(0.22);
        for (int i = 0; i < 3; i++) {
            gc.strokeLine(-2.5 - i * 0.2, -6.3 + i * 0.7, 2.5 + i * 0.2, -6.3 + i * 0.7);
        }

        // big chevron
        gc.setStroke(p.red());
        gc.setLineWidth(0.4);
        gc.beginPath();
        gc.moveTo(-2.7, 3.2);
        gc.lineTo(0, 0.9);
        gc.lineTo(2.7, 3.2);
        gc.stroke();

        // wide glowing visor slit
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#fff0c8")), new Stop(0.4, p.hot()),
                new Stop(1.0, p.red())));
        gc.fillRoundRect(-2.4, -3.8, 4.8, 1.3, 0.65, 0.65);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.14);
        gc.strokeRoundRect(-2.4, -3.8, 4.8, 1.3, 0.65, 0.65);

        // thick nose cannon
        barrel(gc, 0, -11.0, 1.4, 3.8);
        gc.setFill(p.red());
        gc.fillRoundRect(-0.85, -9.6, 1.7, 0.35, 0.12, 0.12);
        muzzleFlash(gc, p, e.getMuzzleFlash(), 1.5);

        if (e.getHitFlash() > 0.02) {
            flashColor(gc, e.getHitFlash());
            for (int s = -1; s <= 1; s += 2) { wardenSlabPath(gc, s); gc.fill(); }
            wardenHullPath(gc);
            gc.fill();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DREADNOUGHT: long forked battlecruiser (crimson-pink)
    // ═════════════════════════════════════════════════════════════════════

    private void dreadHullPath(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(0, -10.2);
        gc.lineTo(1.3, -6.2);
        gc.lineTo(2.4, -1.2);
        gc.lineTo(2.8, 4.0);
        gc.lineTo(2.1, 7.8);
        gc.lineTo(-2.1, 7.8);
        gc.lineTo(-2.8, 4.0);
        gc.lineTo(-2.4, -1.2);
        gc.lineTo(-1.3, -6.2);
        gc.closePath();
    }

    private void dreadFinPath(GraphicsContext gc, double s) {
        gc.beginPath();
        gc.moveTo(s * 2.5, 0.5);
        gc.lineTo(s * 6.5, 3.0);
        gc.lineTo(s * 9.2, 8.6);
        gc.lineTo(s * 5.6, 7.6);
        gc.lineTo(s * 2.6, 6.0);
        gc.closePath();
    }

    private void dreadProngPath(GraphicsContext gc, double s) {
        gc.beginPath();
        gc.moveTo(s * 1.4, -2.0);
        gc.lineTo(s * 3.5, -9.8);
        gc.lineTo(s * 4.5, -9.0);
        gc.lineTo(s * 4.4, -1.0);
        gc.closePath();
    }

    private void drawDreadnought(GraphicsContext gc, EnemyShip e, Pal p, double t) {
        boolean th = e.isThrusting();
        if (th) {
            flame(gc, p, 0.0, 8.6, 1.0, flicker(t, 5.8, 1.5));
            flame(gc, p, -2.6, 8.6, 0.8, flicker(t + 1, 4.4, 1.2));
            flame(gc, p,  2.6, 8.6, 0.8, flicker(t + 2, 4.4, 1.2));
        }

        // swept fins
        for (int s = -1; s <= 1; s += 2) {
            dreadFinPath(gc, s);
            gc.setFill(hGradDir(s, PLATE, HULL, DEEP));
            gc.fill();
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.18);
            gc.stroke();
            gc.setStroke(p.red());
            gc.setLineWidth(0.35);
            gc.strokeLine(s * 6.5, 3.0, s * 9.2, 8.6);
            gc.setStroke(p.dark());
            gc.setLineWidth(0.25);
            gc.strokeLine(s * 3.6, 1.8, s * 7.4, 6.6);
        }

        // forward prongs (the trident)
        for (int s = -1; s <= 1; s += 2) {
            dreadProngPath(gc, s);
            gc.setFill(DEEP);
            gc.fill();
            gc.setStroke(p.red());
            gc.setLineWidth(0.18);
            gc.stroke();
            glowDot(gc, s * 3.9, -9.6, 0.6, p.hot(), 0.9);
        }
        // energy arc between the prong tips
        gc.setStroke(tint(p.hot(), 0.35 + 0.25 * Math.sin(t * 6)));
        gc.setLineWidth(0.25);
        gc.beginPath();
        gc.moveTo(-3.7, -9.6);
        gc.quadraticCurveTo(0, -7.4, 3.7, -9.6);
        gc.stroke();

        // three engines
        for (int i = -1; i <= 1; i++) {
            double cx = i * 2.6;
            gc.setFill(hGrad(DEEP, PLATE, DEEP));
            gc.fillRoundRect(cx - 0.9, 6.4, 1.8, 2.4, 0.5, 0.5);
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.14);
            gc.strokeRoundRect(cx - 0.9, 6.4, 1.8, 2.4, 0.5, 0.5);
            gc.setFill(Color.web("#050507"));
            gc.fillOval(cx - 0.7, 8.3, 1.4, 0.5);
            gc.setStroke(th ? p.hot() : p.dark());
            gc.setLineWidth(0.25);
            gc.strokeOval(cx - 0.85, 8.2, 1.7, 0.7);
        }

        // hull
        dreadHullPath(gc);
        gc.setFill(hGrad(DEEP, PLATE, DEEP));
        gc.fill();
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.2);
        gc.stroke();

        // armor ribs
        gc.setStroke(p.dark());
        gc.setLineWidth(0.28);
        double[][] ribs = {{-3.0, 1.9}, {0.5, 2.55}, {4.0, 2.75}};
        for (double[] r : ribs) gc.strokeLine(-r[1], r[0], r[1], r[0]);

        // central lance (cannon tip at -11)
        barrel(gc, 0, -11.0, 0.8, 2.2);
        gc.setFill(p.red());
        gc.fillRoundRect(-0.5, -10.3, 1.0, 0.25, 0.1, 0.1);
        muzzleFlash(gc, p, e.getMuzzleFlash(), 1.3);

        // slit
        gc.setFill(p.hot());
        gc.fillOval(-0.9, -7.0, 1.8, 0.8);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.12);
        gc.strokeOval(-0.9, -7.0, 1.8, 0.8);

        // glowing core
        double cr = 1.3 + 0.15 * Math.sin(t * 4);
        glowDot(gc, 0, 1.6, cr * 2.2, p.hot(), 0.8);
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.WHITE), new Stop(0.35, p.hot()),
                new Stop(0.8, p.red()), new Stop(1.0, p.dark())));
        gc.fillOval(-cr, 1.6 - cr, cr * 2, cr * 2);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.12);
        gc.strokeOval(-cr, 1.6 - cr, cr * 2, cr * 2);

        if (e.getHitFlash() > 0.02) {
            flashColor(gc, e.getHitFlash());
            for (int s = -1; s <= 1; s += 2) {
                dreadFinPath(gc, s);   gc.fill();
                dreadProngPath(gc, s); gc.fill();
            }
            dreadHullPath(gc);
            gc.fill();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  ANNIHILATOR: demonic crown (red / white-hot)
    // ═════════════════════════════════════════════════════════════════════

    private void annHullPath(GraphicsContext gc) {
        gc.beginPath();
        gc.moveTo(0, -8.8);
        gc.lineTo(2.6, -5.2);
        gc.lineTo(3.7, 0);
        gc.lineTo(3.1, 6.0);
        gc.lineTo(1.7, 8.2);
        gc.lineTo(-1.7, 8.2);
        gc.lineTo(-3.1, 6.0);
        gc.lineTo(-3.7, 0);
        gc.lineTo(-2.6, -5.2);
        gc.closePath();
    }

    private void annBladePath(GraphicsContext gc, double s) {      // rear blades
        gc.beginPath();
        gc.moveTo(s * 3.0, 1.5);
        gc.lineTo(s * 7.0, 3.5);
        gc.lineTo(s * 11.2, 10.0);
        gc.lineTo(s * 6.8, 8.0);
        gc.lineTo(s * 5.2, 10.4);
        gc.lineTo(s * 2.8, 7.0);
        gc.closePath();
    }

    private void annHornPath(GraphicsContext gc, double s) {       // hooked front horns
        gc.beginPath();
        gc.moveTo(s * 2.4, -2.5);
        gc.quadraticCurveTo(s * 7.2, -3.8, s * 9.0, -10.0);
        gc.quadraticCurveTo(s * 5.4, -6.0, s * 5.2, -0.5);
        gc.closePath();
    }

    private void annSpikePath(GraphicsContext gc, double s) {      // crown spikes
        gc.beginPath();
        gc.moveTo(s * 1.6, -6.6);
        gc.lineTo(s * 2.0, -10.4);
        gc.lineTo(s * 0.7, -8.0);
        gc.closePath();
    }

    private void drawAnnihilator(GraphicsContext gc, EnemyShip e, Pal p, double t) {
        int phase = e.getPhase();
        boolean th = e.isThrusting();
        double pulse = 0.5 + 0.5 * Math.sin(t * 3.0);

        // menacing aura: stronger every phase
        double aura = 0.16 + 0.06 * phase + 0.05 * pulse;
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, tint(p.red(), aura)), new Stop(1.0, tint(p.red(), 0.0))));
        gc.fillOval(-14, -14, 28, 28);

        // orbiting armor shards
        for (int i = 0; i < 4; i++) {
            double a = t * 0.9 + i * Math.PI / 2;
            double cx = Math.cos(a) * 11.5, cy = Math.sin(a) * 11.5;
            gc.beginPath();
            gc.moveTo(cx, cy - 1.2);
            gc.lineTo(cx + 0.65, cy);
            gc.lineTo(cx, cy + 1.2);
            gc.lineTo(cx - 0.65, cy);
            gc.closePath();
            gc.setFill(DEEP);
            gc.fill();
            gc.setStroke(p.red());
            gc.setLineWidth(0.14);
            gc.stroke();
        }

        if (th) {
            flame(gc, p, -1.5, 8.4, 1.1, flicker(t, 6.4, 1.6));
            flame(gc, p,  1.5, 8.4, 1.1, flicker(t + 1, 6.4, 1.6));
            flame(gc, p, -4.4, 10.1, 0.8, flicker(t + 2, 4.6, 1.2));
            flame(gc, p,  4.4, 10.1, 0.8, flicker(t + 3, 4.6, 1.2));
        }

        // rear blades + side engines
        for (int s = -1; s <= 1; s += 2) {
            annBladePath(gc, s);
            gc.setFill(hGradDir(s, PLATE, HULL, DEEP));
            gc.fill();
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.2);
            gc.stroke();
            gc.setStroke(p.red());
            gc.setLineWidth(0.45);
            gc.strokeLine(s * 7.0, 3.5, s * 11.2, 10.0);
            gc.setStroke(tint(p.hot(), 0.6));
            gc.setLineWidth(0.15);
            gc.strokeLine(s * 7.0, 3.5, s * 11.2, 10.0);

            double cx = s * 4.4;
            gc.setFill(hGrad(DEEP, PLATE, DEEP));
            gc.fillRoundRect(cx - 0.8, 7.6, 1.6, 2.4, 0.4, 0.4);
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.12);
            gc.strokeRoundRect(cx - 0.8, 7.6, 1.6, 2.4, 0.4, 0.4);
        }

        // hooked horns + crown spikes
        for (int s = -1; s <= 1; s += 2) {
            annHornPath(gc, s);
            gc.setFill(hGradDir(s, PLATE, DEEP, DEEP));
            gc.fill();
            gc.setStroke(p.red());
            gc.setLineWidth(0.22);
            gc.stroke();

            annSpikePath(gc, s);
            gc.setFill(DEEP);
            gc.fill();
            gc.setStroke(p.hot());
            gc.setLineWidth(0.14);
            gc.stroke();
        }

        // twin engine blocks
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * 1.5;
            gc.setFill(hGrad(DEEP, PLATE, DEEP));
            gc.fillRoundRect(cx - 1.0, 6.4, 2.0, 2.6, 0.5, 0.5);
            gc.setStroke(OUTLINE);
            gc.setLineWidth(0.14);
            gc.strokeRoundRect(cx - 1.0, 6.4, 2.0, 2.6, 0.5, 0.5);
        }

        // hull
        annHullPath(gc);
        gc.setFill(hGrad(DEEP, PLATE, DEEP));
        gc.fill();
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.22);
        gc.stroke();

        // plate seams
        gc.setStroke(p.dark());
        gc.setLineWidth(0.3);
        gc.strokeLine(-3.3, 3.8, 3.3, 3.8);
        gc.strokeLine(-2.9, -2.8, 2.9, -2.8);

        // central lance (cannon tip at -11)
        barrel(gc, 0, -11.0, 0.9, 3.0);
        gc.setFill(p.hot());
        gc.fillOval(-0.5, -11.25, 1.0, 0.6);
        muzzleFlash(gc, p, e.getMuzzleFlash(), 1.8);

        // energy veins from the core
        gc.setStroke(tint(p.hot(), 0.55 + 0.3 * pulse));
        gc.setLineWidth(0.22);
        for (int s = -1; s <= 1; s += 2) {
            gc.beginPath();
            gc.moveTo(s * 1.9, 1.0);
            gc.lineTo(s * 4.5, 2.2);
            gc.lineTo(s * 8.6, 7.8);
            gc.stroke();
            gc.beginPath();
            gc.moveTo(s * 1.5, -0.6);
            gc.lineTo(s * 3.0, -3.0);
            gc.lineTo(s * 6.6, -6.8);
            gc.stroke();
        }

        // pulsing reactor core
        double cr = 1.9 + 0.2 * Math.sin(t * 5);
        glowDot(gc, 0, 0.8, cr * 2.6, p.hot(), 0.85);
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.WHITE), new Stop(0.3, p.hot()),
                new Stop(0.75, p.red()), new Stop(1.0, p.dark())));
        gc.fillOval(-cr, 0.8 - cr, cr * 2, cr * 2);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.14);
        gc.strokeOval(-cr, 0.8 - cr, cr * 2, cr * 2);
        gc.setStroke(tint(p.hot(), 0.5));
        gc.setLineWidth(0.18);
        gc.strokeOval(-cr * 1.45, 0.8 - cr * 1.45, cr * 2.9, cr * 2.9);

        // blade / horn tips glow brighter every phase
        for (int s = -1; s <= 1; s += 2) {
            glowDot(gc, s * 9.0, -10.0, 0.5 + 0.25 * phase, p.hot(), 0.9);
            glowDot(gc, s * 11.2, 10.0, 0.4 + 0.2 * phase, p.hot(), 0.8);
        }

        if (e.getHitFlash() > 0.02) {
            flashColor(gc, e.getHitFlash());
            for (int s = -1; s <= 1; s += 2) {
                annBladePath(gc, s); gc.fill();
                annHornPath(gc, s);  gc.fill();
            }
            annHullPath(gc);
            gc.fill();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Lasers: dashed aim line -> solid when locked -> beam(s)
    // ═════════════════════════════════════════════════════════════════════

    private void drawLaser(GraphicsContext gc, EnemyShip e, Pal p, double t) {
        LaserState s = e.getLaserState();
        if (s == LaserState.IDLE) return;

        double u  = e.getUnitScale();
        double my = EnemyShip.MUZZLE_Y;
        int    n  = Math.max(1, e.getBeamCount());

        if (s == LaserState.CHARGING) {
            double total = Math.max(0.01, e.getChargeTime());
            double prog  = Math.min(1, e.getLaserTimer() / total);
            double left  = total - e.getLaserTimer();

            if (left < 1.5) {                                   // dodge warning
                double len = e.getKind().reach / u;
                for (int i = 0; i < n; i++) {
                    gc.save();
                    gc.translate(0, my);
                    gc.rotate(Math.toDegrees(EnemyShip.beamOffset(i, n)));
                    if (e.isLocked()) {                         // solid = locked, it fires HERE
                        gc.setStroke(tint(p.hot(), 0.75));
                        gc.setLineWidth(0.4);
                        gc.strokeLine(0, 0, 0, -len);
                    } else {                                    // dashed = still tracking you
                        gc.setStroke(tint(p.hot(), 0.15 + 0.3 * (1 - left / 1.5)));
                        gc.setLineWidth(0.3);
                        gc.setLineDashes(2.0, 1.5);
                        gc.strokeLine(0, 0, 0, -len);
                        gc.setLineDashes(0);
                    }
                    gc.restore();
                }
            }
            double gr = 0.8 + prog * 3.2 + 0.3 * prog * Math.sin(t * 20);
            gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, tint(Color.WHITE, 0.4 + 0.6 * prog)),
                    new Stop(0.35, tint(p.hot(), 0.6 * prog + 0.2)),
                    new Stop(1.0, tint(p.red(), 0.0))));
            gc.fillOval(-gr, my - gr, gr * 2, gr * 2);
            return;
        }

        // FIRING
        double remain = e.getLaserTimer();
        double env = Math.min(1, (EnemyShip.BEAM_DURATION - remain) / 0.08) * Math.min(1, remain / 0.25);
        if (env <= 0.01) return;

        double hw = Math.max(e.getBeamHalfWidth() / u, 0.35) * (0.94 + 0.06 * Math.sin(t * 55));

        for (int i = 0; i < n; i++) {
            double len = e.getBeamLength(i) / u;
            gc.save();
            gc.translate(0, my);
            gc.rotate(Math.toDegrees(EnemyShip.beamOffset(i, n)));
            beamLayer(gc, len, hw * 3.0, 0.10 * env, p.red());
            beamLayer(gc, len, hw * 1.8, 0.28 * env, p.red());
            beamLayer(gc, len, hw, 0.80 * env, p.hot());
            beamLayer(gc, len, hw * 0.4, env, Color.WHITE);
            gc.restore();
        }

        double fr = hw * 3.0 + 1.5;                              // muzzle flare
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, tint(Color.WHITE, env)),
                new Stop(0.4, tint(p.hot(), 0.7 * env)),
                new Stop(1.0, tint(p.red(), 0.0))));
        gc.fillOval(-fr, my - fr, fr * 2, fr * 2);
    }

    // drawn in a frame whose origin is the muzzle; the beam goes toward -y
    private void beamLayer(GraphicsContext gc, double len, double w, double alpha, Color c) {
        gc.setFill(tint(c, alpha));
        gc.fillRoundRect(-w / 2, -len, w, len, w, w);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Health bars
    // ═════════════════════════════════════════════════════════════════════

    private void drawHealthBar(GraphicsContext gc, EnemyShip e, double sx, double sy, double zoom) {
        Pal p = palFor(e.getKind());
        double w = 44, h = 5;
        double x = sx - w / 2;
        double y = sy - Math.max(e.getRadius() * zoom, 14) - 16;
        double f = clamp(e.getHealthFraction());

        gc.setFill(Color.color(0, 0, 0, 0.65));
        gc.fillRect(x - 1, y - 1, w + 2, h + 2);
        gc.setFill(f > 0.35 ? p.red() : p.hot());
        gc.fillRect(x, y, w * f, h);
        gc.setStroke(tint(p.red(), 0.8));
        gc.setLineWidth(1);
        gc.strokeRect(x - 1, y - 1, w + 2, h + 2);
    }

    // big bar at the top of the screen while a boss is alive
    private void drawBossBar(GraphicsContext gc, EnemyShip boss) {
        Pal p = palFor(boss.getKind());
        boolean fin = boss.getKind() == EnemyKind.BOSS_FINAL;
        double sw = camera.getScreenW();
        double w = Math.min(560, sw * 0.5);
        double h = fin ? 14 : 10;
        double x = (sw - w) / 2, y = 30;
        double f = clamp(boss.getHealthFraction());

        gc.setFill(Color.color(0, 0, 0, 0.7));
        gc.fillRect(x - 2, y - 2, w + 4, h + 4);
        gc.setFill(p.red());
        gc.fillRect(x, y, w * f, h);

        if (fin) {                                               // phase markers
            gc.setStroke(Color.color(0, 0, 0, 0.9));
            gc.setLineWidth(2);
            gc.strokeLine(x + w * 0.33, y, x + w * 0.33, y + h);
            gc.strokeLine(x + w * 0.66, y, x + w * 0.66, y + h);
        }
        gc.setStroke(tint(p.hot(), 0.9));
        gc.setLineWidth(1);
        gc.strokeRect(x - 2, y - 2, w + 4, h + 4);

        gc.setFill(p.hot());
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(boss.getKind().title, sw / 2, y - 8);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Shared drawing helpers
    // ═════════════════════════════════════════════════════════════════════

    private static LinearGradient hGrad(Color a, Color b, Color c) {
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, a), new Stop(0.5, b), new Stop(1.0, c));
    }

    // mirrored per side so the light edge faces inward
    private static LinearGradient hGradDir(int s, Color a, Color b, Color c) {
        Stop s0 = new Stop(0.0, a), s1 = new Stop(0.6, b), s2 = new Stop(1.0, c);
        return s > 0 ? new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, s0, s1, s2)
                : new LinearGradient(1, 0, 0, 0, true, CycleMethod.NO_CYCLE, s0, s1, s2);
    }

    private void barrel(GraphicsContext gc, double cx, double top, double w, double h) {
        gc.setFill(hGrad(DEEP, STEEL, DEEP));
        gc.fillRoundRect(cx - w / 2, top, w, h, w * 0.35, w * 0.35);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(0.12);
        gc.strokeRoundRect(cx - w / 2, top, w, h, w * 0.35, w * 0.35);
    }

    private void glowDot(GraphicsContext gc, double x, double y, double r, Color c, double alpha) {
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, tint(Color.WHITE, alpha)),
                new Stop(0.4, tint(c, alpha * 0.8)),
                new Stop(1.0, tint(c, 0.0))));
        gc.fillOval(x - r, y - r, r * 2, r * 2);
    }

    private void flame(GraphicsContext gc, Pal p, double cx, double y, double halfW, double len) {
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#ffd0c0")),
                new Stop(0.25, p.hot()),
                new Stop(0.7, tint(p.red(), 0.6)),
                new Stop(1.0, tint(p.dark(), 0.0))));
        gc.beginPath();
        gc.moveTo(cx - halfW, y);
        gc.quadraticCurveTo(cx - halfW * 1.3, y + len * 0.55, cx, y + len);
        gc.quadraticCurveTo(cx + halfW * 1.3, y + len * 0.55, cx + halfW, y);
        gc.closePath();
        gc.fill();
    }

    private static double flicker(double t, double base, double amp) {
        return base + amp * (0.6 * Math.sin(t * 40) + 0.4 * Math.sin(t * 67));
    }

    private void muzzleFlash(GraphicsContext gc, Pal p, double flash, double size) {
        if (flash <= 0.02) return;
        double fr = size * (1.0 + flash * 1.6);
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, tint(p.hot(), flash)),
                new Stop(0.35, tint(p.red(), 0.7 * flash)),
                new Stop(1.0, tint(p.dark(), 0.0))));
        gc.fillOval(-fr, -11.4 - fr, fr * 2, fr * 2);
    }

    private void flashColor(GraphicsContext gc, double flash) {
        gc.setFill(Color.color(1, 1, 1, Math.min(1, flash * 0.55)));
    }

    private static Color tint(Color c, double alpha) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), clamp(alpha));
    }

    private static double clamp(double v) { return Math.clamp(v, 0, 1); }
}