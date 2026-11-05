package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.network.NetworkService;
import project.aoop.space_sandbox.service.GameSession;

/** Small co-op status chip, short-lived notices ("Partner joined"), and the key hint. */
@Component
public class CoopHudRenderer {

    private final NetworkService network;

    private String notice = "";
    private long   noticeUntil = 0;

    public CoopHudRenderer(NetworkService network, GameSession session) {
        this.network = network;
        session.setNoticeListener(text -> {
            notice = text;
            noticeUntil = System.nanoTime() + 6_000_000_000L;
        });
    }

    public void render(GraphicsContext gc, double W, double H) {
        gc.save();
        gc.setTextAlign(TextAlignment.LEFT);

        double y = 168;
        if (network.isActive()) {
            boolean online = network.isConnected();
            gc.setFill(Color.color(0, 0, 0, 0.6));
            gc.fillRoundRect(12, y, 210, 44, 8, 8);

            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
            gc.setFill(Color.web("#ff9f43"));
            gc.fillText(network.isHost() ? "CO-OP  ·  HOST (P1)" : "CO-OP  ·  GUEST (P2)", 22, y + 18);

            gc.setFont(Font.font("Courier New", 11));
            gc.setFill(online ? Color.web("#4dffc8") : Color.color(1, 1, 1, 0.55));
            gc.fillText(online ? "● partner online" : "○ waiting for partner", 22, y + 35);
            y += 54;
        }

        if (System.nanoTime() < noticeUntil && !notice.isEmpty()) {
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
            double w = notice.length() * 7.6 + 24;
            gc.setFill(Color.color(0, 0, 0, 0.6));
            gc.fillRoundRect(12, y, w, 26, 8, 8);
            gc.setFill(Color.web("#ffd166"));
            gc.fillText(notice, 24, y + 17);
        }

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(Font.font("Courier New", 11));
        gc.setFill(Color.color(1, 1, 1, 0.30));
        gc.fillText("K = back to menu", W - 14, H - 14);
        gc.restore();
    }
}