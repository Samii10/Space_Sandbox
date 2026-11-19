package project.aoop.space_sandbox.input;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class InputHandler {

    private final Set<KeyCode> held = new HashSet<>();

    private boolean mouseHeld                 = false;
    private boolean mouseJustPressed          = false;
    private boolean mouseJustReleased         = false;
    private boolean secondaryMouseJustPressed = false;
    private boolean mouseSeen                 = false;   // false until the mouse has moved over the window
    private double  mouseX = 0, mouseY = 0;

    public void attach(Scene scene) {
        scene.setOnKeyPressed(e  -> held.add(e.getCode()));
        scene.setOnKeyReleased(e -> held.remove(e.getCode()));
    }

    public void attachMouse(Scene scene) {
        scene.setOnMouseMoved(this::track);
        // while a button is held JavaFX sends DRAGGED instead of MOVED, so the ship
        // would stop aiming whenever you shoot unless we listen to both
        scene.setOnMouseDragged(this::track);

        scene.setOnMousePressed(e -> {
            track(e);
            if (e.getButton() == MouseButton.PRIMARY) {
                mouseHeld        = true;
                mouseJustPressed = true;
            } else if (e.getButton() == MouseButton.SECONDARY) {
                secondaryMouseJustPressed = true;
            }
        });

        scene.setOnMouseReleased(e -> {
            track(e);
            if (e.getButton() == MouseButton.PRIMARY) {
                mouseHeld         = false;
                mouseJustReleased = true;
            }
        });
    }

    private void track(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        mouseSeen = true;
    }

    /** Call once per frame AFTER checking the "just pressed / released" flags. */
    public void clearFrameState() {
        mouseJustPressed          = false;
        mouseJustReleased         = false;
        secondaryMouseJustPressed = false;
    }

    private boolean is(KeyCode k) { return held.contains(k); }

    // ── movement: W/S = forward/back, D/A = right/left (arrow keys too) ──

    /** +1 forward, -1 backward, 0 neither or both. */
    public double forwardAxis() {
        double f = (is(KeyCode.W) || is(KeyCode.UP))   ? 1 : 0;
        double b = (is(KeyCode.S) || is(KeyCode.DOWN)) ? 1 : 0;
        return f - b;
    }

    /** +1 right, -1 left, 0 neither or both. */
    public double strafeAxis() {
        double r = (is(KeyCode.D) || is(KeyCode.RIGHT)) ? 1 : 0;
        double l = (is(KeyCode.A) || is(KeyCode.LEFT))  ? 1 : 0;
        return r - l;
    }

    public boolean thrustForward() { return forwardAxis() > 0; }

    // ── other keys ───────────────────────────────────────────────────────

    public boolean brake()     { return is(KeyCode.SPACE);  }
    public boolean boostKey()  { return is(KeyCode.SHIFT);  }
    public boolean mapKey()    { return is(KeyCode.M);      }
    public boolean closeMap()  { return is(KeyCode.ESCAPE); }
    public boolean leaveCoop() { return is(KeyCode.K);      }
    public boolean levelUp()   { return is(KeyCode.L);      }
    public boolean levelDown() { return is(KeyCode.P);      }
    public boolean zoomIn()    { return is(KeyCode.EQUALS); }
    public boolean zoomOut()   { return is(KeyCode.MINUS);  }
    public boolean shoot()     { return mouseHeld;          }
    public boolean reloadKey() { return is(KeyCode.R); }

    // ── mouse ────────────────────────────────────────────────────────────

    public boolean isMouseHeld()                { return mouseHeld; }
    public boolean isMouseJustPressed()         { return mouseJustPressed; }
    public boolean isMouseJustReleased()        { return mouseJustReleased; }
    public boolean isSecondaryMouseJustPressed(){ return secondaryMouseJustPressed; }
    public boolean hasMouse()                   { return mouseSeen; }
    public double  getMouseX()                  { return mouseX; }
    public double  getMouseY()                  { return mouseY; }
}