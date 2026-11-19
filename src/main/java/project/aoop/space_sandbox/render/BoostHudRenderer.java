package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.service.BoostService;

/** Boost bar, centred above the speed bar. */
@Component
public class BoostHudRenderer {

    private final BoostService boost;

    public BoostHudRenderer(BoostService boost) {
        this.boost = boost;
    }

    public void render(GraphicsContext gc, double W, double H) {
        double bw = 160, bh = 8;
        double x = W / 2 - bw / 2;
        double y = H - 70;

        boolean active = boost.isActive();
        boolean ready  = boost.isReady();
        double  fuel   = boost.getFuel();
        double  pulse  = 0.65 + 0.35 * Math.sin(System.nanoTime() * 1e-9 * 14);

        Color main = active ? Color.color(1.0, 0.60, 0.18, pulse)
                : ready  ? Color.color(0.30, 1.0, 0.80, 0.9)
                : Color.color(0.55, 0.62, 0.78, 0.6);

        gc.save();

        gc.setFill(Color.color(0, 0, 0, 0.5));
        gc.fillRoundRect(x - 6, y - 17, bw + 12, 31, 6, 6);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 11));
        gc.setFill(main);
        gc.fillText(active ? "BOOST" : ready ? "BOOST READY  [SHIFT]" : "BOOST CHARGING", W / 2, y - 5);

        gc.setFill(Color.color(1, 1, 1, 0.10));
        gc.fillRoundRect(x, y, bw, bh, 4, 4);
        gc.setFill(main);
        gc.fillRoundRect(x, y, bw * fuel, bh, 4, 4);

        // tick showing how much fuel is needed to start
        double mx = x + bw * BoostService.MIN_TO_START;
        gc.setStroke(Color.color(1, 1, 1, 0.35));
        gc.setLineWidth(1);
        gc.strokeLine(mx, y - 2, mx, y + bh + 2);

        gc.restore();
    }
}