package com.jchess.mode;

/**
 * Enum representing all supported game modes in JChess.
 *
 * <p>Each constant carries enough metadata to populate the UI (display name, description)
 * and to drive feature-flag decisions (timer, engine requirement) without any {@code if/else}
 * chains in the view layer.</p>
 *
 * <p>To add a new mode: add a constant here and create a matching {@link GameModeController}
 * implementation. No other existing code needs to change.</p>
 */
public enum GameMode {

    LOCAL_MULTIPLAYER(
            "Local Multiplayer",
            "Play with a friend locally on the same board.",
            /* requiresEngine */ false,
            /* timerEnabled   */ true),

    VS_COMPUTER(
            "vs Computer (Stockfish)",
            "Play against the Stockfish engine with selectable difficulty.",
            /* requiresEngine */ true,
            /* timerEnabled   */ true),

    ANALYSIS(
            "Analysis Mode",
            "Explore positions with real-time Stockfish engine evaluation.",
            /* requiresEngine */ true,
            /* timerEnabled   */ false);

    // -----------------------------------------------------------------------
    // Metadata fields
    // -----------------------------------------------------------------------

    private final String displayName;
    private final String description;
    private final boolean requiresEngine;
    private final boolean timerEnabled;

    GameMode(String displayName, String description, boolean requiresEngine, boolean timerEnabled) {
        this.displayName    = displayName;
        this.description    = description;
        this.requiresEngine = requiresEngine;
        this.timerEnabled   = timerEnabled;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** @return {@code true} if this mode needs the Stockfish binary to be available. */
    public boolean requiresEngine() {
        return requiresEngine;
    }

    /** @return {@code true} if the chess clock should count down in this mode. */
    public boolean isTimerEnabled() {
        return timerEnabled;
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Returns the {@code GameMode} matching the given display name, case-insensitively.
     * Falls back to {@link #LOCAL_MULTIPLAYER} if no match is found.
     */
    public static GameMode fromDisplayName(String name) {
        if (name == null) return LOCAL_MULTIPLAYER;
        for (GameMode mode : values()) {
            if (mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return LOCAL_MULTIPLAYER;
    }

    // -----------------------------------------------------------------------
    // Factory method — creates the matching controller for this mode
    // -----------------------------------------------------------------------

    /**
     * Creates and returns a fresh {@link GameModeController} for this game mode.
     *
     * @param difficulty  the engine difficulty; only used by {@link GameMode#VS_COMPUTER}
     * @return a new controller instance ready to be activated via {@link GameModeController#onEnter}
     */
    public GameModeController createController(com.jchess.util.EngineDifficulty difficulty) {
        switch (this) {
            case VS_COMPUTER:
                return new VsComputerMode(difficulty);
            case ANALYSIS:
                return new AnalysisModeController();
            case LOCAL_MULTIPLAYER:
            default:
                return new LocalMultiplayerMode();
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
