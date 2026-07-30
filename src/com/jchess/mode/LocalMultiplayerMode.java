package com.jchess.mode;

import java.awt.Graphics2D;

/**
 * Game mode controller for local two-player (human vs. human) games.
 *
 * <p>This is the simplest mode: it enables the clock, allows undo, and delegates
 * all gameplay to {@link com.jchess.game.GameManager} without any engine involvement.</p>
 */
public class LocalMultiplayerMode implements GameModeController {

    @Override
    public GameMode getMode() {
        return GameMode.LOCAL_MULTIPLAYER;
    }

    @Override
    public void onEnter(GameModeContext context) {
        // No special initialization needed for local multiplayer.
    }

    @Override
    public void onExit() {
        // Nothing to tear down.
    }

    @Override
    public void update(GameModeContext context, boolean mouseJustPressed, boolean mouseJustReleased) {
        // All game-logic is handled by GameManager. The panel calls gm.update() externally.
    }

    @Override
    public void renderOverlay(Graphics2D g2, int panelWidth, int panelHeight) {
        // No mode-specific overlays for local multiplayer.
    }

    @Override
    public boolean isTimerEnabled() {
        return true;
    }

    @Override
    public boolean allowUndo() {
        return true;
    }

    @Override
    public String getStatusText(GameModeContext context) {
        if (context == null || context.getGameManager() == null) {
            return "Local Multiplayer";
        }
        int currentColor = context.getCurrentColor();
        return (currentColor == com.jchess.game.GameManager.WHITE ? "White" : "Black") + "'s turn";
    }
}
