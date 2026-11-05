package project.aoop.space_sandbox.service;

import org.springframework.stereotype.Service;
import project.aoop.space_sandbox.engine.GameState;
import project.aoop.space_sandbox.entity.Ship;
import project.aoop.space_sandbox.network.NetworkService;

import java.util.function.Consumer;

/**
 * The only thing a menu needs to talk to. All callbacks run on the JavaFX thread,
 * and every method must be called from the JavaFX thread.
 */
@Service
public class GameSession {

    private final NetworkService network;
    private final GameState      gameState;
    private final Ship           ship;
    private final LevelService   levelService;

    public GameSession(NetworkService network, GameState gameState,
                       Ship ship, LevelService levelService) {
        this.network = network;
        this.gameState = gameState;
        this.ship = ship;
        this.levelService = levelService;
    }

    public void startNewGame() {
        freshStart();
        gameState.setState(GameState.State.RUNNING);
    }

    public boolean hasSave() { return false; }   // saving / loading is not built yet

    public void hostCoop(Consumer<String> onWaiting, Runnable onPartnerJoined,
                         Consumer<String> onError) {
        freshStart();
        network.hostCoop(onWaiting, () -> {
            gameState.setState(GameState.State.RUNNING);
            onPartnerJoined.run();
        }, onError);
    }

    public void joinCoop(String roomCode, Runnable onConnected, Consumer<String> onError) {
        freshStart();
        network.joinCoop(roomCode, () -> {
            gameState.setState(GameState.State.RUNNING);
            onConnected.run();
        }, onError);
    }

    /** Back out of a lobby. */
    public void cancelCoop() { network.close(); }

    /** Leave the game (and any co-op session) and go back to the menu. */
    public void leaveToMenu() {
        network.close();
        gameState.setState(GameState.State.MENU);
    }

    public boolean isCoopActive() { return network.isActive(); }

    public void setNoticeListener(Consumer<String> listener) { network.setNoticeListener(listener); }

    private void freshStart() {
        ship.reset();
        for (int guard = 0; guard < 10 && levelService.getLevel() > 1; guard++) levelService.levelDown();
    }
}