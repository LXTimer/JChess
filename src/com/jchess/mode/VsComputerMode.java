package com.jchess.mode;

import java.awt.Graphics2D;

import com.jchess.game.GameManager;
import com.jchess.model.piece.PieceType;
import com.jchess.util.EngineDifficulty;
import com.jchess.util.StockfishEngine;

/**
 * Game mode controller for human vs. Stockfish AI games.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detect when it is the engine's turn and fire an asynchronous Stockfish query.</li>
 *   <li>Apply the resulting best-move back to the board via {@link GameModeCallbacks}.</li>
 *   <li>Manage the lifecycle of the {@link StockfishEngine} (start / stop).</li>
 *   <li>Expose thinking state so the panel can show "Stockfish thinking…" in the UI.</li>
 * </ul>
 * </p>
 */
public class VsComputerMode implements GameModeController {

    private EngineDifficulty difficulty;
    private StockfishEngine engine;
    private volatile boolean computerThinking = false;

    public VsComputerMode(EngineDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public EngineDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(EngineDifficulty difficulty) {
        if (difficulty != null) {
            this.difficulty = difficulty;
        }
    }

    /** @return {@code true} while the engine is computing its next move. */
    public boolean isComputerThinking() {
        return computerThinking;
    }

    // -----------------------------------------------------------------------
    // GameModeController lifecycle
    // -----------------------------------------------------------------------

    @Override
    public GameMode getMode() {
        return GameMode.VS_COMPUTER;
    }

    @Override
    public void onEnter(GameModeContext context) {
        // Engine will be lazily started on the first move request.
        computerThinking = false;
    }

    @Override
    public void onExit() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        computerThinking = false;
    }

    // -----------------------------------------------------------------------
    // Update loop — called every game-loop tick by GamePanel
    // -----------------------------------------------------------------------

    @Override
    public void update(GameModeContext context, boolean mouseJustPressed, boolean mouseJustReleased) {
        if (context == null || context.getGameManager() == null) {
            return;
        }
        GameManager gm = context.getGameManager();

        boolean isEngineTurn = gm.currentColor != gm.getPlayerColor();
        if (!isEngineTurn || gm.gameOver || gm.stalemate) {
            return;
        }

        // Keep gm.update ticking (for animations etc.) but block human input
        gm.update(false, false);

        // Trigger engine query once, when there are no pending animations
        if (!computerThinking && !gm.hasAnimations()) {
            triggerEngineMove(context);
        }
    }

    @Override
    public void renderOverlay(Graphics2D g2, int panelWidth, int panelHeight) {
        // No extra overlay drawn by this mode — the thinking indicator is part of getStatusText().
    }

    // -----------------------------------------------------------------------
    // Timer / undo policy
    // -----------------------------------------------------------------------

    @Override
    public boolean isTimerEnabled() {
        return true;
    }

    @Override
    public boolean allowUndo() {
        return true;
    }

    // -----------------------------------------------------------------------
    // Status text
    // -----------------------------------------------------------------------

    @Override
    public String getStatusText(GameModeContext context) {
        if (computerThinking) {
            return "Stockfish thinking…";
        }
        return "Stockfish (" + difficulty.getDisplayName() + ")";
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void triggerEngineMove(GameModeContext context) {
        if (computerThinking) return;

        GameManager gm = context.getGameManager();
        if (gm.gameOver || gm.stalemate) return;

        computerThinking = true;
        String fenSnapshot = context.getFEN();
        int skillLevel = difficulty.getSkillLevel();
        int moveTimeMs = difficulty.getMoveTimeMs();

        System.out.println("[VsComputerMode] Triggering engine move...");
        System.out.println("[VsComputerMode]   FEN: " + fenSnapshot);
        System.out.println("[VsComputerMode]   Skill Level: " + skillLevel + ", Move Time: " + moveTimeMs + "ms");

        new Thread(() -> {
            try {
                if (engine == null) {
                    String path = StockfishEngine.getSavedPath();
                    System.out.println("[VsComputerMode] Starting engine at: " + path);
                    engine = new StockfishEngine(path);
                    if (!engine.start()) {
                        System.err.println("[VsComputerMode] Failed to start engine at: " + path);
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            engine = null;
                            computerThinking = false;
                            context.getCallbacks().promptStockfishPath();
                        });
                        return;
                    }
                    System.out.println("[VsComputerMode] Engine started successfully");
                }

                engine.setSkillLevel(skillLevel);
                System.out.println("[VsComputerMode] Querying engine for best move...");
                String bestMove = engine.getBestMove(fenSnapshot, moveTimeMs);
                System.out.println("[VsComputerMode] Engine result: " + (bestMove != null ? "bestmove " + bestMove : "null"));

                if (bestMove != null && !bestMove.equals("(none)")) {
                    final String bm = bestMove;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        applyMove(bm, context);
                        computerThinking = false;
                    });
                } else {
                    System.out.println("[VsComputerMode] No valid move returned by engine");
                    javax.swing.SwingUtilities.invokeLater(() -> computerThinking = false);
                }
            } catch (Exception ex) {
                System.err.println("[VsComputerMode] Engine exception: " + ex.getMessage());
                javax.swing.SwingUtilities.invokeLater(() -> computerThinking = false);
            }
        }, "stockfish-vs-computer").start();
    }

    private void applyMove(String bestMove, GameModeContext context) {
        if (bestMove.length() < 4) return;

        GameManager gm = context.getGameManager();

        int fromAbsCol = bestMove.charAt(0) - 'a';
        int fromAbsRow = 8 - (bestMove.charAt(1) - '0');
        int toAbsCol   = bestMove.charAt(2) - 'a';
        int toAbsRow   = 8 - (bestMove.charAt(3) - '0');

        PieceType promoType = null;
        if (bestMove.length() == 5) {
            char p = bestMove.charAt(4);
            if (p == 'q') promoType = PieceType.QUEEN;
            else if (p == 'r') promoType = PieceType.ROOK;
            else if (p == 'b') promoType = PieceType.BISHOP;
            else if (p == 'n') promoType = PieceType.KNIGHT;
        }

        // Flip coordinates when board is displayed flipped
        boolean flipped = gm.boardFlipped;
        int fromCol = flipped ? 7 - fromAbsCol : fromAbsCol;
        int fromRow = flipped ? 7 - fromAbsRow : fromAbsRow;
        int toCol   = flipped ? 7 - toAbsCol   : toAbsCol;
        int toRow   = flipped ? 7 - toAbsRow   : toAbsRow;

        // Safety check: verify a piece actually exists at from-square
        boolean pieceFound = false;
        for (com.jchess.model.Piece piece : gm.pieces) {
            if (piece.col == fromCol && piece.row == fromRow) {
                pieceFound = true;
                break;
            }
        }
        if (!pieceFound) {
            System.err.println("[VsComputerMode] Move rejected — no piece at from-square. move=" + bestMove);
            return;
        }

        context.getCallbacks().applyEngineMove(fromCol, fromRow, toCol, toRow, promoType);
    }
}
