package com.jchess.mode;

import com.jchess.model.piece.PieceType;

/**
 * Callback interface bridging mode controllers back to the hosting
 * {@link com.jchess.view.GamePanel} without creating a compile-time dependency
 * on the panel's concrete class.
 *
 * <p>The panel implements this interface and passes itself (or a lambda/anonymous
 * class) to every {@link GameModeController} via {@link GameModeContext}.</p>
 */
public interface GameModeCallbacks {

    /**
     * Applies a move produced by an external engine (e.g. Stockfish) onto the
     * game board and triggers a repaint.
     *
     * @param fromCol   display column the piece moves from (may be flipped)
     * @param fromRow   display row    the piece moves from
     * @param toCol     display column the piece moves to
     * @param toRow     display row    the piece moves to
     * @param promoType the promotion piece type, or {@code null} if not a promotion
     */
    void applyEngineMove(int fromCol, int fromRow, int toCol, int toRow, PieceType promoType);

    /**
     * Opens the OS file-chooser dialog so the user can locate the Stockfish
     * binary when the previously saved path is invalid.
     */
    void promptStockfishPath();

    /**
     * Schedules a repaint of the game panel on the Swing EDT.
     */
    void requestRepaint();
}
