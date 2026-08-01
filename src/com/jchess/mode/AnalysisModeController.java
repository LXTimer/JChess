package com.jchess.mode;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;

import com.jchess.game.GameManager;
import com.jchess.model.Board;
import com.jchess.util.MoveRecord;
import com.jchess.util.StockfishEngine;


public class AnalysisModeController implements GameModeController {

    // -----------------------------------------------------------------------
    // Layout constants (mirror GamePanel's eval-bar constants)
    // -----------------------------------------------------------------------
    private static final int EVAL_BAR_X      = Board.ORIGIN_X + Board.SIZE * 8 + 1;
    private static final int EVAL_BAR_WIDTH  = 8;
    private static final int EVAL_BAR_HEIGHT = Board.SIZE * 8;
    private static final int SIDE_PANEL_X    = Board.ORIGIN_X + Board.SIZE * 8 + 10;
    private static final int SIDE_PANEL_Y    = 35;
    private static final int ANALYSIS_SEARCH_DEPTH = 30;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private StockfishEngine engine;
    private volatile boolean analysisThinking  = false;
    private volatile String  evaluationText    = "--";
    private volatile int     whiteScoreCp      = 0;   // positive = white advantage
    private volatile String  lastAnalysedFen   = null;
    private volatile String  bestMove          = null;
    private volatile String  principalVariation = null;
    private volatile List<String> principalVariations = Collections.emptyList();

    // -----------------------------------------------------------------------
    // GameModeController lifecycle
    // -----------------------------------------------------------------------

    @Override
    public GameMode getMode() {
        return GameMode.ANALYSIS;
    }

    @Override
    public void onEnter(GameModeContext context) {
        analysisThinking = false;
        evaluationText   = "--";
        whiteScoreCp     = 0;
        lastAnalysedFen  = null;
        bestMove         = null;
        principalVariation = null;
        principalVariations = Collections.emptyList();
        // Engine will be lazily started on the first evaluation request.
    }

    @Override
    public void onExit() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        analysisThinking = false;
        bestMove = null;
        principalVariation = null;
        principalVariations = Collections.emptyList();
    }

    // -----------------------------------------------------------------------
    // Update — triggers background evaluation when position has changed
    // -----------------------------------------------------------------------

    @Override
    public void update(GameModeContext context, boolean mouseJustPressed, boolean mouseJustReleased) {
        if (context == null || context.getGameManager() == null) {
            return;
        }
        GameManager gm = context.getGameManager();

        // Don't start a new eval while one is running, or in transient states.
        // Note: gameOver/stalemate reflect the LIVE game, not the viewed position,
        // so we must NOT block analysis of historical positions before a terminal state.
        if (analysisThinking || gm.promotion
                || gm.activeP != null || gm.hasAnimations()) {
            return;
        }

        String currentFen = gm.getViewFEN();
        if (!currentFen.equals(lastAnalysedFen)) {
            bestMove = null;
            principalVariation = null;
            principalVariations = Collections.emptyList();
        }
        if (currentFen.equals(lastAnalysedFen)) {
            return; // Position unchanged — no new evaluation needed
        }

        // Try to ensure the engine is running; silently skip if unavailable
        if (!ensureEngine(context)) {
            evaluationText  = "Engine unavailable";
            whiteScoreCp    = 0;
            principalVariation = null;
            principalVariations = Collections.emptyList();
            lastAnalysedFen = currentFen;
            return;
        }

        analysisThinking = true;
        final String fenSnapshot = currentFen;
        // Derive the side-to-move from the viewed position, not the live game,
        // so score conversion is correct when browsing history.
        final boolean whiteToMoveSnapshot;
        int viewMoveIndex = gm.getViewMoveIndex();
        if (viewMoveIndex == -1) {
            whiteToMoveSnapshot = gm.currentColor == GameManager.WHITE;
        } else {
            whiteToMoveSnapshot = (viewMoveIndex % 2 == 0);
        }

        new Thread(() -> {
            try {
                String engineBestMove = engine.analyzeAtDepth(
                        fenSnapshot,
                        ANALYSIS_SEARCH_DEPTH,
                        eval -> javax.swing.SwingUtilities.invokeLater(() -> {
                            if (!fenSnapshot.equals(context.getViewFEN())) return;
                            evaluationText = eval.toDisplayString(whiteToMoveSnapshot);
                            whiteScoreCp = eval.toWhiteCentipawns(whiteToMoveSnapshot);
                            principalVariation = eval.getPrincipalVariation();
                            principalVariations = eval.getPrincipalVariations();
                            if (principalVariation != null && !principalVariation.isEmpty()) {
                                bestMove = principalVariation.split("\\s+", 2)[0];
                            }
                            // Backfill move-quality metadata for the position we just evaluated.
                            // The position has {@code viewMoveIndex} half-moves played:
                            //   - If viewMoveIndex > 0 → this is the "after" state of move N-1
                            //   - If viewMoveIndex < totalMoves → this is the "before" state of move N
                            backfillMoveQuality(context.getGameManager(), viewMoveIndex, whiteScoreCp);
                            context.getCallbacks().requestRepaint();
                        }),
                        () -> fenSnapshot.equals(context.getViewFEN())
                                || context.getGameManager().activeP != null);

                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (fenSnapshot.equals(context.getViewFEN())) {
                        bestMove = engineBestMove;
                        lastAnalysedFen = fenSnapshot;
                    }
                    analysisThinking = false;
                    context.getCallbacks().requestRepaint();
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    evaluationText  = "N/A";
                    whiteScoreCp    = 0;
                    bestMove        = null;
                    principalVariation = null;
                    principalVariations = Collections.emptyList();
                    analysisThinking = false;
                    context.getCallbacks().requestRepaint();
                });
            }
        }, "stockfish-analysis").start();
    }

    // -----------------------------------------------------------------------
    // Overlay rendering — eval bar and numeric score
    // -----------------------------------------------------------------------

    @Override
    public void renderOverlay(Graphics2D g2, int panelWidth, int panelHeight) {
        drawEvaluationBar(g2);
        drawEvaluationText(g2);
        drawPrincipalVariation(g2);
    }

    private void drawEvaluationBar(Graphics2D g2) {
        int barX      = EVAL_BAR_X;
        int barY      = Board.ORIGIN_Y;
        int barWidth  = EVAL_BAR_WIDTH;
        int barHeight = EVAL_BAR_HEIGHT;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(new Color(14, 16, 20, 210));
        g2.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);

        // White / Black split
        int boundedScore = Math.max(-1000, Math.min(1000, whiteScoreCp));
        // whiteShare=0 → all black (white losing badly); 1 → all white (white winning)
        double whiteShare = (boundedScore + 1000.0) / 2000.0;
        // The bar is drawn top=black, bottom=white: split Y counts from the top
        int splitY = barY + (int) Math.round(barHeight * (1.0 - whiteShare));

        // White fill (bottom portion)
        g2.setColor(new Color(235, 240, 235, 225));
        g2.fillRoundRect(barX + 1, splitY, barWidth - 2, Math.max(0, barY + barHeight - splitY - 1), 5, 5);

        // Black fill (top portion)
        g2.setColor(new Color(92, 48, 48, 225));
        g2.fillRoundRect(barX + 1, barY + 1, barWidth - 2, Math.max(0, splitY - barY - 1), 5, 5);

        // Border
        g2.setColor(new Color(255, 255, 255, 55));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(barX, barY, barWidth, barHeight, 6, 6);

        // Divider line tinted by advantage direction
        g2.setColor(boundedScore >= 0
            ? new Color(110, 205, 135, 220)
            : new Color(225, 95, 95, 220));
        g2.fillRect(barX + 1, Math.max(barY + 1, splitY - 1), barWidth - 2, 2);
    }

    private void drawEvaluationText(Graphics2D g2) {
        g2.setFont(new Font("Roboto", Font.PLAIN, 22));
        g2.setColor(new Color(215, 222, 230));
        String text = evaluationText;
        g2.drawString(text, SIDE_PANEL_X + 16, SIDE_PANEL_Y + 40);
    }

    private void drawPrincipalVariation(Graphics2D g2) {
        List<String> variations = principalVariations;
        if (variations.isEmpty() && principalVariation != null && !principalVariation.isEmpty()) {
            variations = Collections.singletonList(principalVariation);
        }
        if (variations.isEmpty()) {
            return;
        }
        g2.setFont(new Font("Roboto", Font.PLAIN, 12));
        g2.setColor(new Color(170, 180, 195));
        FontMetrics metrics = g2.getFontMetrics();
        int maxWidth = 268;
        for (int variationIndex = 0; variationIndex < Math.min(2, variations.size()); variationIndex++) {
            StringBuilder line = new StringBuilder();
            String[] moves = variations.get(variationIndex).split("\\s+");
            for (int moveIndex = 0; moveIndex < Math.min(6, moves.length); moveIndex++) {
                String move = moves[moveIndex];
                String candidate = line.length() == 0 ? move : line + " " + move;
                if (metrics.stringWidth(candidate) > maxWidth) {
                    break;
                }
                line = new StringBuilder(candidate);
            }
            if (line.length() > 0) {
                g2.drawString(line.toString(), SIDE_PANEL_X + 16,
                        SIDE_PANEL_Y + 62 + variationIndex * (metrics.getHeight() + 1));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Move-quality backfill
    // -----------------------------------------------------------------------

    /**
     * Populates evaluation metadata on the {@link MoveRecord}s adjacent to the
     * position that was just evaluated, then recomputes their quality.
     *
     * <p>The evaluated position has {@code viewMoveIndex} half-moves played.
     * That position is the "after" state of move {@code viewMoveIndex - 1} and
     * the "before" state of move {@code viewMoveIndex}.  When both evals are
     * known for a moved, {@link MoveRecord#recomputeQuality()} classifies the
     * move as BEST / GOOD / INACCURACY / MISTAKE / BLUNDER.</p>
     *
     * @param gm             the game manager holding the move list
     * @param viewMoveIndex  number of half-moves played at the evaluated position
     *                       ({@code -1} means the live end of the game)
     * @param whiteScoreCp   engine evaluation (white-perspective, centipawns)
     */
    private void backfillMoveQuality(GameManager gm, int viewMoveIndex, int whiteScoreCp) {
        if (gm == null || gm.moves == null) {
            return;
        }
        int totalMoves = gm.moves.size();
        int positionIndex = (viewMoveIndex == -1) ? totalMoves : viewMoveIndex;

        // If this position has a preceding move, record it as that move's "after" eval.
        if (positionIndex >= 1 && positionIndex - 1 < totalMoves) {
            MoveRecord before = gm.moves.get(positionIndex - 1);
            before.evalAfterCp = Integer.valueOf(whiteScoreCp);
            before.recomputeQuality();
        }

        // If a move starts from this position, record it as that move's "before" eval.
        if (positionIndex < totalMoves) {
            MoveRecord after = gm.moves.get(positionIndex);
            after.evalBeforeCp = Integer.valueOf(whiteScoreCp);
            after.recomputeQuality();
        }
    }

    /**
     * Computes the engine-perspective quality colour of a move's current state.
     *
     * @return the colour used to draw that move in the move log
     */
    public static Color getMoveQualityColor(MoveRecord.MoveQuality quality) {
        switch (quality) {
            case BEST:       return new Color(126, 255, 140);
            case GOOD:       return new Color(160, 220, 160);
            case INACCURACY: return new Color(230, 210, 110);
            case MISTAKE:    return new Color(230, 160, 80);
            case BLUNDER:    return new Color(230, 80, 80);
            default:         return new Color(210, 215, 225);
        }
    }

    // -----------------------------------------------------------------------
    // Timer / undo policy
    // -----------------------------------------------------------------------

    @Override
    public boolean isTimerEnabled() {
        return false; // No clock in analysis mode
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
        return analysisThinking ? "Evaluating..." : evaluationText;
    }

    // -----------------------------------------------------------------------
    // Accessors used by GamePanel for display
    // -----------------------------------------------------------------------

    /** @return the current evaluation display string (e.g. "+1.5", "M3"). */
    public String getEvaluationText() {
        return evaluationText;
    }

    /** @return {@code true} while the engine is computing. */
    public boolean isAnalysisThinking() {
        return analysisThinking;
    }

    /** @return the latest UCI best move, or {@code null} while unavailable. */
    public String getBestMove() {
        return bestMove;
    }

    /** @return the latest engine principal variation in UCI notation. */
    public String getPrincipalVariation() {
        return principalVariation;
    }

    public List<String> getPrincipalVariations() {
        return principalVariations;
    }

    /** Applies the current best move when requested by the analysis-mode shortcut. */
    public void playBestMove(GameModeContext context) {
        if (context == null || bestMove == null) {
            return;
        }
        GameManager gm = context.getGameManager();
        if (gm == null || gm.gameOver || gm.stalemate || gm.promotion
                || gm.activeP != null || gm.hasAnimations()) {
            return;
        }

        String move = bestMove;
        if (move.length() < 4) {
            return;
        }

        int fromAbsCol = move.charAt(0) - 'a';
        int fromAbsRow = 8 - (move.charAt(1) - '0');
        int toAbsCol = move.charAt(2) - 'a';
        int toAbsRow = 8 - (move.charAt(3) - '0');
        if (fromAbsCol < 0 || fromAbsCol > 7 || fromAbsRow < 0 || fromAbsRow > 7
                || toAbsCol < 0 || toAbsCol > 7 || toAbsRow < 0 || toAbsRow > 7) {
            return;
        }

        com.jchess.model.piece.PieceType promoType = null;
        if (move.length() > 4) {
            switch (move.charAt(4)) {
                case 'q': promoType = com.jchess.model.piece.PieceType.QUEEN; break;
                case 'r': promoType = com.jchess.model.piece.PieceType.ROOK; break;
                case 'b': promoType = com.jchess.model.piece.PieceType.BISHOP; break;
                case 'n': promoType = com.jchess.model.piece.PieceType.KNIGHT; break;
                default: return;
            }
        }

        int fromCol = gm.boardFlipped ? 7 - fromAbsCol : fromAbsCol;
        int fromRow = gm.boardFlipped ? 7 - fromAbsRow : fromAbsRow;
        int toCol = gm.boardFlipped ? 7 - toAbsCol : toAbsCol;
        int toRow = gm.boardFlipped ? 7 - toAbsRow : toAbsRow;
        bestMove = null;
        context.getCallbacks().applyEngineMove(fromCol, fromRow, toCol, toRow, promoType);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Ensures the Stockfish engine is running. Returns {@code true} if the engine
     * is ready to accept queries, {@code false} otherwise.
     */
    private boolean ensureEngine(GameModeContext context) {
        if (engine != null) {
            return true;
        }
        String path = StockfishEngine.getSavedPath();
        engine = new StockfishEngine(path);
        if (engine.start()) {
            engine.setMultiPv(2);
            return true;
        }
        engine = null;
        return false;
    }
}
