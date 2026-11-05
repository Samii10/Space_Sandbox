package project.aoop.space_sandbox.render;

import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextInputDialog;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.input.InputHandler;
import project.aoop.space_sandbox.service.GameSession;

import java.util.ArrayList;
import java.util.List;

/** TEMPORARY menu: New Game / Co-op (Host, Join). Mouse only. Replaced by the real menu later. */
@Component
public class TempMenu {

    private enum Screen { MAIN, COOP, HOSTING, JOINING }
    private enum Action { NEW_GAME, COOP, HOST, JOIN, BACK, CANCEL }

    private record Button(Action action, String label, double x, double y, double w, double h) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private final GameSession  session;
    private final InputHandler input;

    private Screen  screen   = Screen.MAIN;
    private String  roomCode = "";
    private String  message  = "";
    private boolean error    = false;

    public TempMenu(GameSession session, InputHandler input) {
        this.session = session;
        this.input = input;
    }

    public void reset() {
        screen = Screen.MAIN;
        message = "";
        error = false;
    }

    // ── input ────────────────────────────────────────────────────────────

    public void update(double W, double H) {
        if (!input.isMouseJustReleased()) return;
        double mx = input.getMouseX(), my = input.getMouseY();
        for (Button b : buttons(W, H)) {
            if (b.contains(mx, my)) {
                click(b.action());
                return;
            }
        }
    }

    private void click(Action action) {
        switch (action) {
            case NEW_GAME -> { message = ""; session.startNewGame(); }
            case COOP     -> { screen = Screen.COOP; message = ""; }
            case BACK     -> { screen = Screen.MAIN; message = ""; }
            case HOST     -> hostGame();
            case JOIN     -> joinGame();
            case CANCEL   -> { session.cancelCoop(); screen = Screen.COOP; message = ""; }
        }
    }

    private void hostGame() {
        message = "";
        session.hostCoop(
                code -> { roomCode = code; screen = Screen.HOSTING; },
                ()   -> { screen = Screen.MAIN; },                         // the game starts by itself
                err  -> { screen = Screen.COOP; message = err; error = true; });
    }

    private void joinGame() {
        // a dialog can't be opened from inside the animation timer, so schedule it
        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Join co-op");
            dialog.setHeaderText("Enter the room code shown on the host's screen");
            dialog.setContentText("Room code:");
            dialog.showAndWait().ifPresent(code -> {
                screen = Screen.JOINING;
                message = "";
                session.joinCoop(code,
                        () -> { screen = Screen.MAIN; },
                        err -> { screen = Screen.COOP; message = err; error = true; });
            });
        });
    }

    // ── layout ───────────────────────────────────────────────────────────

    private List<Button> buttons(double W, double H) {
        double bw = 320, bh = 54, gap = 16;
        double x = W / 2 - bw / 2;
        double y = H * 0.46;
        List<Button> list = new ArrayList<>();

        switch (screen) {
            case MAIN -> {
                list.add(new Button(Action.NEW_GAME, "New Game", x, y, bw, bh));
                list.add(new Button(Action.COOP, "Co-op  (2 players)", x, y + (bh + gap), bw, bh));
            }
            case COOP -> {
                list.add(new Button(Action.HOST, "Host Game", x, y, bw, bh));
                list.add(new Button(Action.JOIN, "Join Game", x, y + (bh + gap), bw, bh));
                list.add(new Button(Action.BACK, "Back", x, y + 2 * (bh + gap), bw, bh));
            }
            case HOSTING, JOINING ->
                    list.add(new Button(Action.CANCEL, "Cancel", x, H * 0.64, bw, bh));
        }
        return list;
    }

    // ── drawing ──────────────────────────────────────────────────────────

    public void render(GraphicsContext gc, double W, double H) {
        gc.save();
        gc.setTextAlign(TextAlignment.CENTER);

        gc.setFill(Color.color(1, 1, 1, 0.95));
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 56));
        gc.fillText("SPACE SANDBOX", W / 2, H * 0.26);
        gc.setFill(Color.color(1, 1, 1, 0.35));
        gc.setFont(Font.font("Courier New", 12));
        gc.fillText("temporary menu", W / 2, H * 0.26 + 26);

        double mx = input.getMouseX(), my = input.getMouseY();
        for (Button b : buttons(W, H)) {
            boolean hover = b.contains(mx, my);
            gc.setFill(hover ? Color.color(0.30, 1.0, 0.80, 0.18) : Color.color(1, 1, 1, 0.06));
            gc.fillRoundRect(b.x(), b.y(), b.w(), b.h(), 12, 12);
            gc.setStroke(hover ? Color.color(0.30, 1.0, 0.80, 0.8) : Color.color(1, 1, 1, 0.18));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(b.x(), b.y(), b.w(), b.h(), 12, 12);
            gc.setFill(hover ? Color.web("#4dffc8") : Color.color(1, 1, 1, 0.9));
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 18));
            gc.fillText(b.label(), b.x() + b.w() / 2, b.y() + b.h() / 2 + 6);
        }

        if (screen == Screen.HOSTING) {
            gc.setFill(Color.color(1, 1, 1, 0.55));
            gc.setFont(Font.font("Courier New", 13));
            gc.fillText("ROOM CODE", W / 2, H * 0.40);
            gc.setFill(Color.web("#ffd166"));
            gc.setFont(Font.font("Courier New", FontWeight.BOLD, 52));
            gc.fillText(roomCode, W / 2, H * 0.50);
            gc.setFill(Color.color(1, 1, 1, 0.55));
            gc.setFont(Font.font("Courier New", 14));
            gc.fillText("Waiting for your partner to join...", W / 2, H * 0.57);
        } else if (screen == Screen.JOINING) {
            gc.setFill(Color.color(1, 1, 1, 0.7));
            gc.setFont(Font.font("Courier New", 16));
            gc.fillText("Connecting...", W / 2, H * 0.52);
        }

        if (!message.isEmpty()) {
            gc.setFill(error ? Color.web("#ff6b6b") : Color.web("#4dffc8"));
            gc.setFont(Font.font("Courier New", 14));
            gc.fillText(message, W / 2, H * 0.82);
        }

        gc.setFill(Color.color(1, 1, 1, 0.30));
        gc.setFont(Font.font("Courier New", 12));
        gc.fillText("Co-op needs both players on the same Wi-Fi / network", W / 2, H - 24);
        gc.restore();
    }
}