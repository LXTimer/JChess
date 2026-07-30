package com.jchess.mode;

/**
 * Interface defining the lifecycle and behaviour contract for each game mode.
 *
 * <p>Each concrete mode (Local Multiplayer, vs Computer, Analysis) implements this interface
 * so that {@link com.jchess.view.GamePanel} can delegate all mode-specific logic without
 * needing to know which mode is active.</p>
 *
 * <p>Lifecycle order: {@code onEnter} → repeated {@code update}/{@code renderOverlay} calls → {@code onExit}.</p>
 */
public interface GameModeController {

    /** Returns the {@link GameMode} constant this controller handles. */
    GameMode getMode();

    /**
     * Called once when this mode becomes active (after the game has been reset and is ready
     * to start). Use this to start background engine threads, etc.
     *
     * @param context  shared context giving access to the game manager and panel
     */
    void onEnter(GameModeContext context);

    /**
     * Called once when this mode is being torn down (e.g. returning to title, restarting).
     * Release any background resources (engine threads, timers) here.
     */
    void onExit();

    /**
     * Called every game-loop tick by {@link com.jchess.view.GamePanel}.
     * Implement AI turn scheduling, analysis evaluation triggering, etc.
     *
     * @param context           shared context
     * @param mouseJustPressed  true on the first tick the left mouse button is down
     * @param mouseJustReleased true on the first tick the left mouse button is released
     */
    void update(GameModeContext context, boolean mouseJustPressed, boolean mouseJustReleased);

    /**
     * Called inside {@code paintComponent} after the board and common UI have been drawn.
     * Implement mode-specific overlays such as the evaluation bar or thinking indicators.
     *
     * @param g2          the active {@link java.awt.Graphics2D} context
     * @param panelWidth  total width of the game panel
     * @param panelHeight total height of the game panel
     */
    void renderOverlay(java.awt.Graphics2D g2, int panelWidth, int panelHeight);

    /**
     * Returns {@code true} if the chess clock should count down in this mode.
     * Analysis mode typically has no time pressure.
     */
    boolean isTimerEnabled();

    /**
     * Returns {@code true} if the undo button should be available in this mode.
     * Competitive vs-computer modes may wish to disable undo.
     */
    boolean allowUndo();

    /**
     * Returns a human-readable status string for the current turn/state displayed in the
     * side panel (e.g. "Your turn", "Stockfish thinking…", "Evaluating…").
     *
     * @param context shared context
     * @return non-null status text
     */
    String getStatusText(GameModeContext context);
}
