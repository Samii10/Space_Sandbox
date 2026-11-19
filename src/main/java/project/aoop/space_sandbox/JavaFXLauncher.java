package project.aoop.space_sandbox;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import project.aoop.space_sandbox.camera.GameCamera;
import project.aoop.space_sandbox.engine.GameLoop;
import project.aoop.space_sandbox.engine.GameState;
import project.aoop.space_sandbox.input.InputHandler;
import project.aoop.space_sandbox.render.GameRenderer;

public class JavaFXLauncher extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(SpaceSandboxApplication.class).run();
    }

    @Override
    public void start(Stage stage) {
        double W = 1280, H = 720;

        Canvas canvas = new Canvas(W, H);
        Pane root = new Pane(canvas);

        GameCamera camera = springContext.getBean(GameCamera.class);
        camera.setScreenSize(W, H);

        GameRenderer renderer = springContext.getBean(GameRenderer.class);
        renderer.setCanvas(canvas);

        InputHandler inputHandler = springContext.getBean(InputHandler.class);
        GameLoop     gameLoop     = springContext.getBean(GameLoop.class);

        // Tell GameLoop the canvas size so it can handle minimap clicks
        gameLoop.setCanvasSize(W, H);

        root.widthProperty().addListener((obs, oldValue, newValue) -> {
            double width = newValue.doubleValue();
            canvas.setWidth(width);
            camera.setScreenSize(width, canvas.getHeight());
            gameLoop.setCanvasSize(width, canvas.getHeight());
        });
        root.heightProperty().addListener((obs, oldValue, newValue) -> {
            double height = newValue.doubleValue();
            canvas.setHeight(height);
            camera.setScreenSize(canvas.getWidth(), height);
            gameLoop.setCanvasSize(canvas.getWidth(), height);
        });

        GameState gameState = springContext.getBean(GameState.class);
        gameState.setState(GameState.State.MENU);

        Scene scene = new Scene(root, W, H);

        // Keyboard
        inputHandler.attach(scene);

        // Mouse — InputHandler tracks press/held/release
        // GameLoop reads these every frame and decides what to do
        inputHandler.attachMouse(scene);

        // Scroll wheel — zoom
        scene.setOnScroll(e -> {
            if (e.getDeltaY() > 0) camera.adjustZoom(1.1);
            else                   camera.adjustZoom(0.9);
        });

        stage.setTitle("Space Sandbox");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
        stage.setMaximized(true);

        gameLoop.start();
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }
}