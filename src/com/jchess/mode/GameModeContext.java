package com.jchess.mode;

import com.jchess.game.GameManager;

/**
 * Shared context object passed to every {@link GameModeController}.
 *
 * <p>This avoids tight coupling between mode controllers and the concrete
 * {@link com.jchess.view.GamePanel} class.  The panel creates a single
 * {@code GameModeContext} instance and passes it to each controller on
 * {@code onEnter}, {@code update}, and {@code renderOverlay}.</p>
 */
public final class GameModeContext {

    private final GameManager gameManager;
    private final GameModeCallbacks callbacks;

    /**
     * Constructs a new context.
     *
     * @param gameManager the game manager instance shared across the session
     * @param callbacks   panel-level callback bridge for operations such as
     *                    applying an engine move, triggering a repaint, or
     *                    opening the Stockfish configuration dialog
     */
    public GameModeContext(GameManager gameManager, GameModeCallbacks callbacks) {
        this.gameManager = gameManager;
        this.callbacks = callbacks;
    }

    /** Returns the active {@link GameManager}. */
    public GameManager getGameManager() {
        return gameManager;
    }

    /** Returns the panel-level callback bridge. */
    public GameModeCallbacks getCallbacks() {
        return callbacks;
    }

    // -----------------------------------------------------------------------
    // Convenience delegations
    // -----------------------------------------------------------------------

    /** @see GameManager#currentColor */
    public int getCurrentColor() {
        return gameManager.currentColor;
    }

    /** @see GameManager#getPlayerColor() */
    public int getPlayerColor() {
        return gameManager.getPlayerColor();
    }

    /** Returns {@code true} when it is the computer/engine's turn. */
    public boolean isEngineTurn() {
        return gameManager.currentColor != gameManager.getPlayerColor();
    }

    /** @see GameManager#getFEN() */
    public String getFEN() {
        return gameManager.getFEN();
    }

    /** @see GameManager#getViewFEN() */
    public String getViewFEN() {
        return gameManager.getViewFEN();
    }
}
