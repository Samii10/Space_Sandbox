package project.aoop.space_sandbox.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.springframework.stereotype.Component;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.entity.RemoteShip;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.entity.ShipGeometry;
import project.aoop.space_sandbox.network.NetworkService;

/** Draws the partner's ship, a name tag with a health bar, or an arrow when they are off-screen. */
@Component
public class RemoteShipRenderer {

    private static final Color PARTNER = Color.web("#ff9f43");

    private final GameCamera     camera;
    private final ShipRenderer   shipRenderer;
    private final RemoteShip     remote;
    private final NetworkService network;

    public RemoteShipRenderer(GameCamera camera, ShipRenderer shipRenderer,
                              RemoteShip remote, NetworkService network) {
        this.camera = camera;
        this.shipRenderer = shipRenderer;
        this.remote = remote;
        this.network = network;
    }

    public void render(GraphicsContext gc) {
        if (!remote.isVisible()) return;

        Ship ghost = remote.getGhost();
        double W = camera.getScreenW(), H = camera.getScreenH();
        double sx = camera.toScreenX(ghost.getX());
        double sy = camera.toScreenY(ghost.getY());
        double unit = ShipGeometry.WORLD_PER_UNIT * camera.getZoom();
        String name = network.isHost() ? "P2" : "P1";
        boolean dead = ghost.getHealth() <= 0;

        boolean onScreen = sx > -60 && sx < W + 60 && sy > -60 && sy < H + 60;

        gc.save();
        if (onScreen) {
            if (!dead) shipRenderer.renderRemote(gc, camera, ghost, remote.isThrusting());
            drawTag(gc, sx, sy - Math.max(unit * 9.5, 22), name, ghost.getHealth(), dead);
        } else {
            drawEdgeArrow(gc, sx, sy, W, H, name, dead);
        }
        gc.restore();
    }

    private void drawTag(GraphicsContext gc, double x, double y, String name, int health, boolean dead) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
        gc.setFill(PARTNER);
        gc.fillText(dead ? name + "  ✕ DESTROYED" : name, x, y);

        if (!dead) {
            double bw = 46;
            double ratio = Math.max(0, Math.min(1, health / 100.0));
            gc.setFill(Color.color(0, 0, 0, 0.55));
            gc.fillRoundRect(x - bw / 2, y + 5, bw, 5, 3, 3);
            gc.setFill(ratio > 0.5 ? Color.color(0.3, 1.0, 0.5, 0.9)
                    : ratio > 0.25 ? Color.color(1.0, 0.85, 0.2, 0.9)
                    : Color.color(1.0, 0.3, 0.2, 0.9));
            gc.fillRoundRect(x - bw / 2, y + 5, bw * ratio, 5, 3, 3);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawEdgeArrow(GraphicsContext gc, double sx, double sy,
                               double W, double H, String name, boolean dead) {
        double cx = W / 2, cy = H / 2;                 // the camera is locked on your ship
        double dx = sx - cx, dy = sy - cy;
        double dist = Math.hypot(dx, dy) / camera.getZoom();

        double k = Math.min((cx - 56) / Math.max(Math.abs(dx), 1e-6),
                (cy - 56) / Math.max(Math.abs(dy), 1e-6));
        double ex = cx + dx * k, ey = cy + dy * k;
        double ang = Math.atan2(dy, dx);

        gc.save();
        gc.translate(ex, ey);
        gc.rotate(Math.toDegrees(ang));
        gc.setFill(PARTNER);
        gc.fillPolygon(new double[]{16, -9, -4, -9}, new double[]{0, -10, 0, 10}, 4);
        gc.restore();

        double tx = ex - Math.cos(ang) * 36, ty = ey - Math.sin(ang) * 36;
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(PARTNER);
        gc.setFont(Font.font("Courier New", FontWeight.BOLD, 12));
        gc.fillText(dead ? name + " ✕" : name, tx, ty - 2);
        gc.setFill(Color.color(1, 1, 1, 0.6));
        gc.setFont(Font.font("Courier New", 10));
        gc.fillText((int) dist + " u", tx, ty + 10);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}