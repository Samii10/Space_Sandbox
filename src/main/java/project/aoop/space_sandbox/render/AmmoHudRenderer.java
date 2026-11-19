package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.service.CombatService;
import project.aoop.space_sandbox.service.LevelService;

/** Weapon box, bottom-left above the hull bar: ammo + reload, or laser charge. */
@Component
public class AmmoHudRenderer {

    private final CombatService combat;
    private final LevelService  levelService;

    public AmmoHudRenderer(CombatService combat, LevelService levelService) {
        this.combat = combat;
        this.levelService = levelService;
    }

    public void render(GraphicsContext gc, double W, double H) {
        double x = 8, y = H - 128, w = 186, h = 56;
        boolean laser = combat.isLaserMode();

        gc.save();
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(x, y, w, h, 8, 8);

        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        gc.setFill(laser ? Color.web("#5fb8ff") : Color.web("#ffb347"));
        gc.fillText(laser ? "LASER" : "BULLETS", x + 10, y + 17);

        if (laser) {
            gc.setFont(Font.font("Courier New", 11));
            gc.setFill(Color.color(1, 1, 1, 0.6));
            gc.fillText(String.format("full charge: %.1f s", levelService.getLaserChargeTime()),
                    x + 10, y + 33);
            bar(gc, x + 10, y + 40, w - 20, 7, combat.getCharge(), Color.web("#5fb8ff"));
        } else {
            int ammo = combat.getAmmo();
            int cap  = combat.getMagazineSize();

            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 22));
            gc.setFill(ammo == 0 ? Color.web("#ff6b6b")
                    : ammo <= cap * 0.25 ? Color.web("#ffd166")
                    : Color.WHITE);
            gc.fillText(ammo + " / " + cap, x + 10, y + 44);

            double bx = x + 104, bw = w - 114;
            gc.setFont(Font.font("Courier New", 11));
            if (combat.isReloading()) {
                gc.setFill(Color.web("#ffd166"));
                gc.fillText("RELOADING", bx, y + 33);
                bar(gc, bx, y + 39, bw, 6, combat.getReloadProgress(), Color.web("#ffd166"));
            } else {
                gc.setFill(Color.color(1, 1, 1, 0.45));
                gc.fillText(ammo < cap ? "[R] reload" : "ready", bx, y + 40);
            }
        }
        gc.restore();
    }

    private void bar(GraphicsContext gc, double x, double y, double w, double h,
                     double ratio, Color color) {
        ratio = Math.max(0, Math.min(1, ratio));
        gc.setFill(Color.color(1, 1, 1, 0.10));
        gc.fillRoundRect(x, y, w, h, h, h);
        gc.setFill(color);
        gc.fillRoundRect(x, y, w * ratio, h, h, h);
    }
}