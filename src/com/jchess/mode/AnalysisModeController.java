package com.jchess.mode;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

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
    private volatile String  observedFen       = null;
    private volatile String  bestMove          = null;
    private volatile String  principalVariation = null;
    private volatile List<String> principalVariations = Collections.emptyList();
    private volatile boolean shuttingDown = false;
    private volatile long lifecycleGeneration = 0;
    private volatile long settingsGeneration = 0;
    private volatile boolean engineEnabled = true;
    private volatile String disabledFenSnapshot = null;
    private final Rectangle engineToggleRect = new Rectangle();
    private final Rectangle settingsButtonRect = new Rectangle();
    private final List<Rectangle> pvMoveRects = new java.util.ArrayList<>();
    private final List<String> pvMovePrefixes = new java.util.ArrayList<>();
    private int hoveredPvMove = -1;
    private int searchTimeSeconds = 8;
    private int multiPv = 1;
    private int threads = 1;
    private int memoryMb = 16;
    private BufferedImage settingsIcon;
    private volatile boolean settingsHovered;

    // -----------------------------------------------------------------------
    // GameModeController lifecycle
    // -----------------------------------------------------------------------

    @Override
    public GameMode getMode() {
        return GameMode.ANALYSIS;
    }

    @Override
    public void onEnter(GameModeContext context) {
        lifecycleGeneration++;
        shuttingDown = false;
        engineEnabled = true;
        disabledFenSnapshot = null;
        analysisThinking = false;
        evaluationText   = "--";
        whiteScoreCp     = 0;
        lastAnalysedFen  = null;
        observedFen      = null;
        bestMove         = null;
        principalVariation = null;
        principalVariations = Collections.emptyList();
        searchTimeSeconds = 8;
        multiPv = 1;
        threads = 1;
        memoryMb = 16;
        settingsIcon = loadSettingsIcon();
        // Engine will be lazily started on the first evaluation request.
    }

    @Override
    public void onExit() {
        lifecycleGeneration++;
        shuttingDown = true;
        StockfishEngine engineToStop = engine;
        engine = null;
        if (engineToStop != null) {
            // Avoid blocking Swing's EDT while an analysis call is still running.
            new Thread(engineToStop::forceStop, "stockfish-analysis-stop").start();
        }
        engineEnabled = true;
        disabledFenSnapshot = null;
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
        if (shuttingDown) {
            return;
        }
        GameManager gm = context.getGameManager();
        String currentFen = gm.getViewFEN();
        observedFen = currentFen;

        if (!engineEnabled) {
            analysisThinking = false;
            if (disabledFenSnapshot == null) {
                disabledFenSnapshot = currentFen;
            } else if (!currentFen.equals(disabledFenSnapshot)) {
                evaluationText = "--";
                whiteScoreCp = 0;
                bestMove = null;
                principalVariation = null;
                principalVariations = Collections.emptyList();
                disabledFenSnapshot = currentFen;
                lastAnalysedFen = currentFen;
            }
            return;
        }

        // Don't start a new eval while one is running, or in transient states.
        // Note: gameOver/stalemate reflect the LIVE game, not the viewed position,
        // so we must NOT block analysis of historical positions before a terminal state.
        if (analysisThinking || gm.promotion
                || gm.activeP != null || gm.hasAnimations()) {
            return;
        }

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
        engine.setMultiPv(multiPv);
        engine.setThreads(threads);
        engine.setHash(memoryMb);

        analysisThinking = true;
        final StockfishEngine activeEngine = engine;
        if (activeEngine == null) {
            analysisThinking = false;
            return;
        }
        final String fenSnapshot = currentFen;
        final long requestGeneration = lifecycleGeneration;
        final long requestSettingsGeneration = settingsGeneration;
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
                String engineBestMove = activeEngine.analyzeAtTime(
                        fenSnapshot,
                        searchTimeSeconds * 1000,
                        multiPv,
                        eval -> javax.swing.SwingUtilities.invokeLater(() -> {
                            if (shuttingDown || !engineEnabled || requestGeneration != lifecycleGeneration) return;
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
                        () -> !shuttingDown && engineEnabled
                                && requestGeneration == lifecycleGeneration
                                && fenSnapshot.equals(observedFen));

                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (shuttingDown || !engineEnabled || requestGeneration != lifecycleGeneration) return;
                    if (fenSnapshot.equals(context.getViewFEN())) {
                        bestMove = engineBestMove;
                        if (requestSettingsGeneration == settingsGeneration) {
                            lastAnalysedFen = fenSnapshot;
                        }
                    }
                    analysisThinking = false;
                    context.getCallbacks().requestRepaint();
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (shuttingDown || !engineEnabled || requestGeneration != lifecycleGeneration) return;
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

    public boolean isSettingsButtonHit(int x, int y) {
        return settingsButtonRect.contains(x, y);
    }

    public void setSettingsHovered(boolean hovered) {
        settingsHovered = hovered;
    }

    public int getSearchTimeSeconds() {
        return searchTimeSeconds;
    }

    public int getThreads() {
        return threads;
    }

    public int getMultiPv() {
        return multiPv;
    }

    public int getMemoryMb() {
        return memoryMb;
    }

    public void setAnalysisSettings(int searchTimeSeconds, int multiPv, int threads, int memoryMb) {
        this.searchTimeSeconds = Math.max(2, Math.min(60, searchTimeSeconds));
        this.multiPv = Math.max(1, Math.min(4, multiPv));
        this.threads = Math.max(1, Math.min(4, threads));
        this.memoryMb = Math.max(16, Math.min(512, memoryMb));
        settingsGeneration++;
        lastAnalysedFen = null;
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
        int evalX = SIDE_PANEL_X + 16;
        int evalY = SIDE_PANEL_Y + 40;
        g2.drawString(text, evalX, evalY);

        FontMetrics metrics = g2.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int toggleX = evalX + textWidth + 14;
        int toggleY = evalY - 14;
        int toggleW = 34;
        int toggleH = 16;
        drawEngineToggle(g2, toggleX, toggleY, toggleW, toggleH);
        int settingsX = toggleX + toggleW + 8;
        int settingsY = toggleY - 3;
        settingsButtonRect.setBounds(settingsX, settingsY, 22, 22);
        g2.setColor(settingsHovered ? new Color(52, 98, 155, 220) : new Color(40, 45, 55, 210));
        g2.fillRoundRect(settingsX, settingsY, 22, 22, 7, 7);
        if (settingsIcon != null) {
            g2.drawImage(settingsIcon, settingsX + 2, settingsY + 2, 18, 18, null);
        }
    }

    private BufferedImage loadSettingsIcon() {
        try {
            BufferedImage source;
            InputStream resource = AnalysisModeController.class.getResourceAsStream(
                    "/com/jchess/resources/icons/settings.png");
            if (resource != null) {
                try (InputStream in = resource) {
                    source = ImageIO.read(in);
                }
            } else {
                source = ImageIO.read(new File("src/com/jchess/resources/icons/settings.png"));
            }
            if (source == null) return null;
            BufferedImage icon = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int argb = source.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xff;
                    if (alpha == 0) continue;
                    icon.setRGB(x, y, (alpha << 24) | 0xffdce3eb);
                }
            }
            return icon;
        } catch (Exception e) {
            return null;
        }
    }

    private void drawEngineToggle(Graphics2D g2, int x, int y, int w, int h) {
        engineToggleRect.setBounds(x, y, w, h);

        g2.setColor(engineEnabled ? new Color(88, 176, 112, 220) : new Color(92, 98, 108, 220));
        g2.fillRoundRect(x, y, w, h, h, h);

        g2.setColor(new Color(255, 255, 255, 90));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, w, h, h, h);

        int knobSize = h - 4;
        int knobX = engineEnabled ? x + w - knobSize - 2 : x + 2;
        int knobY = y + 2;
        g2.setColor(new Color(242, 246, 250, 235));
        g2.fillOval(knobX, knobY, knobSize, knobSize);
    }

    private void drawPrincipalVariation(Graphics2D g2) {
        pvMoveRects.clear();
        pvMovePrefixes.clear();
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
            String[] moves = variations.get(variationIndex).split("\\s+");
            int baseline = SIDE_PANEL_Y + 62 + variationIndex * (metrics.getHeight() + 1);
            int x = SIDE_PANEL_X + 16;
            StringBuilder prefix = new StringBuilder();
            for (int moveIndex = 0; moveIndex < Math.min(6, moves.length); moveIndex++) {
                String move = moves[moveIndex];
                int moveWidth = metrics.stringWidth(move);
                if (x + moveWidth > SIDE_PANEL_X + 16 + maxWidth) {
                    break;
                }
                if (prefix.length() > 0) {
                    x += metrics.stringWidth(" ");
                    prefix.append(' ');
                }
                prefix.append(move);
                int moveX = x;
                boolean hovered = pvMoveRects.size() == hoveredPvMove;
                g2.setColor(hovered ? new Color(100, 170, 255) : new Color(170, 180, 195));
                g2.drawString(move, moveX, baseline);
                pvMoveRects.add(new Rectangle(moveX, baseline - metrics.getAscent(), moveWidth, metrics.getHeight()));
                pvMovePrefixes.add(prefix.toString());
                x += moveWidth;
            }
        }
    }

    public void updatePvHover(int x, int y) {
        int next = -1;
        for (int i = 0; i < pvMoveRects.size(); i++) {
            if (pvMoveRects.get(i).contains(x, y)) {
                next = i;
                break;
            }
        }
        hoveredPvMove = next;
    }

    public boolean isPvMoveHovered() {
        return hoveredPvMove >= 0;
    }

    public boolean playClickedPvMove(GameModeContext context, int x, int y) {
        for (int i = 0; i < pvMoveRects.size(); i++) {
            if (pvMoveRects.get(i).contains(x, y)) {
                if (context == null || context.getGameManager() == null
                        || context.getGameManager().hasAnimations()) {
                    return true;
                }
                String[] moves = pvMovePrefixes.get(i).split("\\s+");
                for (String move : moves) {
                    if (!applyUciMove(context, move)) break;
                }
                bestMove = null;
                principalVariation = null;
                principalVariations = Collections.emptyList();
                context.getCallbacks().requestRepaint();
                return true;
            }
        }
        return false;
    }

    private boolean applyUciMove(GameModeContext context, String move) {
        if (move == null || move.length() < 4) return false;
        int fromAbsCol = move.charAt(0) - 'a';
        int fromAbsRow = 8 - (move.charAt(1) - '0');
        int toAbsCol = move.charAt(2) - 'a';
        int toAbsRow = 8 - (move.charAt(3) - '0');
        if (fromAbsCol < 0 || fromAbsCol > 7 || fromAbsRow < 0 || fromAbsRow > 7
                || toAbsCol < 0 || toAbsCol > 7 || toAbsRow < 0 || toAbsRow > 7) return false;

        com.jchess.model.piece.PieceType promoType = null;
        if (move.length() > 4) {
            switch (move.charAt(4)) {
                case 'q': promoType = com.jchess.model.piece.PieceType.QUEEN; break;
                case 'r': promoType = com.jchess.model.piece.PieceType.ROOK; break;
                case 'b': promoType = com.jchess.model.piece.PieceType.BISHOP; break;
                case 'n': promoType = com.jchess.model.piece.PieceType.KNIGHT; break;
                default: return false;
            }
        }
        com.jchess.game.GameManager gm = context.getGameManager();
        int fromCol = gm.boardFlipped ? 7 - fromAbsCol : fromAbsCol;
        int fromRow = gm.boardFlipped ? 7 - fromAbsRow : fromAbsRow;
        int toCol = gm.boardFlipped ? 7 - toAbsCol : toAbsCol;
        int toRow = gm.boardFlipped ? 7 - toAbsRow : toAbsRow;
        context.getCallbacks().applyEngineMove(fromCol, fromRow, toCol, toRow, promoType);
        return true;
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
     * Computes the move-log text colour for blunders.
     *
     * @return red for blunders, neutral text otherwise
     */
    public static Color getMoveQualityColor(MoveRecord.MoveQuality quality) {
        return quality == MoveRecord.MoveQuality.BLUNDER
                ? new Color(230, 80, 80)
                : new Color(210, 215, 225);
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

    public boolean isEngineEnabled() {
        return engineEnabled;
    }

    public boolean isToggleHit(int x, int y) {
        return engineToggleRect.contains(x, y);
    }

    public boolean handleToggleClick(GameModeContext context, int x, int y) {
        if (!isToggleHit(x, y)) {
            return false;
        }
        setEngineEnabled(context, !engineEnabled);
        return true;
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
            engine.setMultiPv(multiPv);
            engine.setThreads(threads);
            engine.setHash(memoryMb);
            return true;
        }
        engine = null;
        return false;
    }

    private void setEngineEnabled(GameModeContext context, boolean enabled) {
        if (engineEnabled == enabled) {
            return;
        }

        engineEnabled = enabled;
        analysisThinking = false;

        if (!engineEnabled) {
            disabledFenSnapshot = context != null ? context.getViewFEN() : null;
            bestMove = null;
            principalVariation = null;
            principalVariations = Collections.emptyList();
            StockfishEngine engineToStop = engine;
            engine = null;
            if (engineToStop != null) {
                new Thread(engineToStop::forceStop, "stockfish-analysis-toggle-stop").start();
            }
            return;
        }

        disabledFenSnapshot = null;
        lastAnalysedFen = null;
    }
}
