package com.jchess.view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.BoxLayout;
import javax.swing.Timer;

import com.jchess.game.GameManager;
import com.jchess.input.Mouse;
import com.jchess.model.Board;
import com.jchess.model.Piece;
import com.jchess.model.piece.PieceType;

public class GamePanel extends JPanel {

    // Declare all the variables
    public static final int WIDTH = 900;
    public static final int HEIGHT = 600;
    private static final int FPS = 60;
    private static final int TIMER_DELAY = 1000 / FPS;
    private static final int BOARD_PIXEL_SIZE = Board.SIZE * 8;
    private static final int SIDE_PANEL_X = Board.ORIGIN_X + BOARD_PIXEL_SIZE + 10;
    private static final int SIDE_PANEL_Y = 35;
    private static final int SIDE_PANEL_WIDTH = 300;
    private static final int SIDE_PANEL_HEIGHT = 525;
    private static final int EVAL_BAR_X = Board.ORIGIN_X + BOARD_PIXEL_SIZE + 1;
    private static final int EVAL_BAR_WIDTH = 8;
    private static final int EVAL_BAR_HEIGHT = BOARD_PIXEL_SIZE;
    private static final int SIDE_PANEL_CENTER_X = SIDE_PANEL_X + SIDE_PANEL_WIDTH / 2;
    private static final int BLACK_TURN_Y = SIDE_PANEL_Y + 40;
    private static final int BLACK_CHECK_Y = BLACK_TURN_Y + 22;
    private static final int WHITE_TURN_Y = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT - 40;
    private static final int WHITE_CHECK_Y = WHITE_TURN_Y - 22;
    private static final int PROMOTION_LABEL_Y = SIDE_PANEL_Y + 100;
    private static final int MOVE_DOT_OUTER_SIZE = 30;
    private static final int MOVE_DOT_INNER_SIZE = 15;
    private static final float BACKGROUND_ALPHA = 0.70f;

    private Timer gameTimer;
    private final Board board;
    private final Mouse mouse;
    private final GameManager gm;
    private BufferedImage backgroundImage;
    private int whiteTimeRemaining; // in seconds
    private int blackTimeRemaining; // in seconds
    private long lastSecondTimestamp;
    private boolean timerPaused = false;
    private long pauseStartTime = 0;
    private static final int INITIAL_TIME_SECONDS = 6000;
    private BufferedImage ResignIcon;
    private BufferedImage undoIcon;
    private final GamePanelMoveLogRenderer moveLogRenderer;
    private boolean mousePressedLastFrame = false;
    private boolean rightPressedLastFrame = false;
    private boolean leftPressedLastFrame = false;
    private int rightClickStartCol = -1;
    private int rightClickStartRow = -1;
    private boolean rightClickDragging = false;
    private final ArrayList<Point> rightClickHighlights = new ArrayList<>();
    private final ArrayList<Arrow> rightClickArrows = new ArrayList<>();
    private java.awt.Rectangle menuButtonRect = new java.awt.Rectangle();
    private java.awt.Rectangle menuDropdownRect = new java.awt.Rectangle();
    private java.awt.Rectangle menuFlipRect = new java.awt.Rectangle();
    private java.awt.Rectangle menuSettingsRect = new java.awt.Rectangle();
    private java.awt.Rectangle menuPgnRect = new java.awt.Rectangle();
    private java.awt.Rectangle menuFenRect = new java.awt.Rectangle();
    private boolean menuOpen = false;
    private java.awt.Rectangle resignWhiteRect = new java.awt.Rectangle();
    private java.awt.Rectangle resignBlackRect = new java.awt.Rectangle();
    private java.awt.Rectangle undoWhiteRect = new java.awt.Rectangle();
    private java.awt.Rectangle undoBlackRect = new java.awt.Rectangle();
    private java.awt.Rectangle navStartRect  = new java.awt.Rectangle(); // |<  go to start
    private java.awt.Rectangle navPrevRect   = new java.awt.Rectangle(); // <   go back one move
    private java.awt.Rectangle navNextRect   = new java.awt.Rectangle(); // >   go forward one move
    private java.awt.Rectangle navEndRect    = new java.awt.Rectangle(); // >|  go to end (live)
    private java.awt.Rectangle restartButtonRect = new java.awt.Rectangle();
    private java.awt.Rectangle titleButtonRect = new java.awt.Rectangle();
    private boolean isPlayerWhite = true;
    private TitlePanel titlePanel;
    private int lastInitialTimeSeconds = INITIAL_TIME_SECONDS;

    // Stockfish engine support
    private boolean isVsComputer = false;
    private boolean isAnalysisMode = false;
    private boolean computerThinking = false;
    private boolean analysisThinking = false;
    private com.jchess.util.StockfishEngine stockfishEngine;
    private com.jchess.util.EngineDifficulty engineDifficulty = com.jchess.util.EngineDifficulty.MEDIUM;
    private volatile String analysisEvaluationText = "--";
    private volatile int analysisEvaluationWhiteScore = 0;
    private volatile String lastAnalysisFen = null;
    private static final int ANALYSIS_SEARCH_TIME_MS = 250;

    public void setVsComputer(boolean vsComputer) {
        this.isVsComputer = vsComputer;
    }

    public void setAnalysisMode(boolean analysisMode) {
        this.isAnalysisMode = analysisMode;
    }

    public void setEngineDifficulty(com.jchess.util.EngineDifficulty difficulty) {
        if (difficulty != null) {
            this.engineDifficulty = difficulty;
        }
    }

    public com.jchess.util.EngineDifficulty getEngineDifficulty() {
        return engineDifficulty;
    }

    // Constructor
    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.black);
        setDoubleBuffered(true);

        mouse = new Mouse();
        board = new Board();
        gm = new com.jchess.game.GameManager(mouse);
        backgroundImage = loadBackground();
        ResignIcon = loadResignIcon();
        undoIcon = loadUndoIcon();
        moveLogRenderer = new GamePanelMoveLogRenderer(gm, mouse, ResignIcon, undoIcon,
            menuButtonRect, resignWhiteRect, resignBlackRect, undoWhiteRect, undoBlackRect,
            navStartRect, navPrevRect, navNextRect, navEndRect);
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
        addMouseWheelListener(e -> {
            int notches = e.getWheelRotation();
            gm.scrollMoveLog(notches);
            repaint();
        });
        
        // Pause timer when window loses focus (Feature 37)
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (!timerPaused && !isGameFinished() && gameTimer != null && gameTimer.isRunning()) {
                    timerPaused = true;
                    pauseStartTime = System.currentTimeMillis();
                }
            }
            
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (timerPaused && pauseStartTime > 0) {
                    long pauseDuration = System.currentTimeMillis() - pauseStartTime;
                    lastSecondTimestamp += pauseDuration;
                    timerPaused = false;
                }
            }
        });
        
        // Keyboard shortcuts
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                handleKeyPress(e);
            }
        });
        setFocusable(true);
        requestFocus();
    }

    public void setPlayerColor(boolean isPlayerWhite) {
        this.isPlayerWhite = isPlayerWhite;
        gm.playerColor = isPlayerWhite ? GameManager.WHITE : GameManager.BLACK;
    }

    public void setTitlePanel(TitlePanel titlePanel) {
        this.titlePanel = titlePanel;
    }

    // Start method
    public void startGame(int initialTimeSeconds) {
        lastInitialTimeSeconds = initialTimeSeconds;
        resetMatch(initialTimeSeconds);
        startTimerLoop();
        setVisible(true);
        requestFocusInWindow();
    }

    public void restartGame() {
        resetMatch(lastInitialTimeSeconds);
        setVisible(true);
        repaint();
    }

    public void returnToTitle() {
        if (gameTimer != null) {
            gameTimer.stop();
        }
        if (stockfishEngine != null) {
            stockfishEngine.stop();
            stockfishEngine = null;
        }
        // Reset the game state so a clean board shows underneath the title panel
        resetMatch(lastInitialTimeSeconds);
        setVisible(true);
        if (titlePanel != null) {
            titlePanel.showTitlePanel();
        }
        repaint();
    }

    private void resetMatch(int initialTimeSeconds) {
        gm.resetGameState();

        if (stockfishEngine != null) {
            stockfishEngine.stop();
            stockfishEngine = null;
        }
        computerThinking = false;
        analysisThinking = false;
        analysisEvaluationText = "--";
        analysisEvaluationWhiteScore = 0;
        lastAnalysisFen = null;

        mouse.pressed = false;
        mouse.rightPressed = false;
        mousePressedLastFrame = false;
        rightPressedLastFrame = false;
        leftPressedLastFrame = false;
        rightClickStartCol = -1;
        rightClickStartRow = -1;
        rightClickDragging = false;
        rightClickHighlights.clear();
        rightClickArrows.clear();

        whiteTimeRemaining = initialTimeSeconds;
        blackTimeRemaining = initialTimeSeconds;
        lastSecondTimestamp = System.currentTimeMillis();

        if (!isPlayerWhite) {
            gm.toggleFlipBoard();
        }

        repaint();
    }

    private void startTimerLoop() {
        if (gameTimer == null) {
            gameTimer = new Timer(TIMER_DELAY, e -> {
                boolean mouseJustPressed = mouse.pressed && !mousePressedLastFrame;
                boolean mouseJustReleased = !mouse.pressed && mousePressedLastFrame;
                mousePressedLastFrame = mouse.pressed;

                if (isGameFinished()) {
                    if (mouseJustPressed && handleEndScreenClick()) {
                        repaint();
                        return;
                    }
                } else if (mouseJustPressed && menuButtonRect.contains(mouse.x, mouse.y)) {
                    toggleMenu();
                } else if (menuOpen && mouseJustPressed && menuFlipRect.contains(mouse.x, mouse.y)) {
                    closeMenu();
                    gm.toggleFlipBoard();
                } else if (menuOpen && mouseJustPressed && menuSettingsRect.contains(mouse.x, mouse.y)) {
                    closeMenu();
                    showSettingsDialog();
                } else if (menuOpen && mouseJustPressed && menuPgnRect.contains(mouse.x, mouse.y)) {
                    closeMenu();
                    showPgnDialog();
                } else if (menuOpen && mouseJustPressed && menuFenRect.contains(mouse.x, mouse.y)) {
                    closeMenu();
                    showFenDialog();
                } else if (menuOpen && mouseJustPressed && !menuDropdownRect.contains(mouse.x, mouse.y)) {
                    closeMenu();
                } else if (mouseJustPressed && undoWhiteRect.contains(mouse.x, mouse.y) && gm.canUndo()) {
                    gm.undoLastMove();
                } else if (mouseJustPressed && undoBlackRect.contains(mouse.x, mouse.y) && gm.canUndo()) {
                    gm.undoLastMove();
                } else if (mouseJustPressed && navStartRect.contains(mouse.x, mouse.y)) {
                    gm.viewStart();
                } else if (mouseJustPressed && navPrevRect.contains(mouse.x, mouse.y)) {
                    gm.viewPrevious();
                } else if (mouseJustPressed && navNextRect.contains(mouse.x, mouse.y)) {
                    gm.viewNext();
                } else if (mouseJustPressed && navEndRect.contains(mouse.x, mouse.y)) {
                    gm.viewEnd();
                } else if (mouseJustPressed && resignWhiteRect.contains(mouse.x, mouse.y) && !gm.gameOver) {
                    gm.resign(0);
                } else if (mouseJustPressed && resignBlackRect.contains(mouse.x, mouse.y) && !gm.gameOver) {
                    gm.resign(1);
                } else if (gm.getViewMoveIndex() == -1) {
                    if (isVsComputer && gm.currentColor != gm.getPlayerColor() && !gm.gameOver && !gm.stalemate) {
                        gm.update(false, false);
                        if (!computerThinking && !gm.hasAnimations()) {
                            triggerComputerMove();
                        }
                    } else {
                        gm.update(mouseJustPressed, mouseJustReleased);
                    }
                }

                updateRightClickAnnotations();
                updateAnalysisEvaluation();
                updateTimer();
                repaint();
            });
            gameTimer.setCoalesce(false);
        }

        if (!gameTimer.isRunning()) {
            gameTimer.start();
        }
    }

    private boolean handleEndScreenClick() {
        if (restartButtonRect.contains(mouse.x, mouse.y)) {
            restartGame();
            return true;
        }

        if (titleButtonRect.contains(mouse.x, mouse.y)) {
            returnToTitle();
            return true;
        }

        return false;
    }

    private boolean isGameFinished() {
        return gm.gameOver || gm.stalemate;
    }

    // Handle keyboard shortcuts
    private void handleKeyPress(java.awt.event.KeyEvent e) {
        boolean shouldRepaint = true;
        int keyCode = e.getKeyCode();
        
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT:
                // Navigate to previous move
                gm.viewPrevious();
                break;
                
            case java.awt.event.KeyEvent.VK_RIGHT:
                // Navigate to next move
                gm.viewNext();
                break;
                
            case java.awt.event.KeyEvent.VK_UP:
                // Navigate to start position
                gm.viewStart();
                break;
                
            case java.awt.event.KeyEvent.VK_DOWN:
                // Navigate to end position (live game)
                gm.viewEnd();
                break;
                
            case java.awt.event.KeyEvent.VK_F:
                // Flip board
                gm.toggleFlipBoard();
                break;
                
            case java.awt.event.KeyEvent.VK_Z:
                // Undo last move (Ctrl+Z)
                if ((e.getModifiers() & java.awt.event.InputEvent.CTRL_MASK) != 0) {
                    if (gm.canUndo()) {
                        gm.undoLastMove();
                    }
                }
                break;
                
            default:
                shouldRepaint = false;
                break;
        }
        
        if (shouldRepaint) {
            repaint();
        }
    }

    // Helper method for loading background images
    private BufferedImage loadBackground() {
        try (InputStream in = GamePanel.class.getResourceAsStream("/com/jchess/resources/background.jpg")) {

            if (in == null) {
                System.err.println("Failed to load image: /resources/background.jpg");
                return null;
            }

            return ImageIO.read(in);

        } catch (Exception e) {
            System.err.println("Failed to load image: /resources/background.jpg");
            return null;
        }
    }

    // Helper method for loading the resign icon
    private BufferedImage loadResignIcon() {
        try (InputStream in = GamePanel.class.getResourceAsStream("/com/jchess/resources/icons/resign.png")) {
            if (in == null) {
                System.err.println("Failed to load image: /resources/icons/resign.png");
                return null;
            }
            return scaleImage(ImageIO.read(in), 56, 56);
        } catch (Exception e) {
            System.err.println("Failed to load image: /resources/icons/resign.png");
            return null;
        }
    }

    // Helper method for loading the undo icon
    private BufferedImage loadUndoIcon() {
        try (InputStream in = GamePanel.class.getResourceAsStream("/com/jchess/resources/icons/undo.png")) {
            if (in == null) {
                System.err.println("Failed to load image: /resources/icons/undo.png");
                return null;
            }
            return scaleImage(ImageIO.read(in), 20, 20);
        } catch (Exception e) {
            System.err.println("Failed to load image: /resources/icons/undo.png");
            return null;
        }
    }

    // Helper method for scaling images
    private BufferedImage scaleImage(BufferedImage src, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();
        return scaled;
    }

    @Override
    // Main rendering method for the game panel
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Draw the game background
        if (backgroundImage == null) {

            g2.setColor(new Color(20, 21, 24));
            g2.fillRect(0, 0, getWidth(), getHeight());

        } else {

            Composite oldComposite = g2.getComposite();

            g2.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            BACKGROUND_ALPHA));

                double scale = Math.max(
                    (double) getWidth() / backgroundImage.getWidth(),
                    (double) getHeight() / backgroundImage.getHeight());

                int drawWidth = (int) (backgroundImage.getWidth() * scale);
                int drawHeight = (int) (backgroundImage.getHeight() * scale);

            int drawX = (getWidth() - drawWidth) / 2;
            int drawY = (getHeight() - drawHeight) / 2;

                g2.drawImage(
                    backgroundImage,
                    drawX,
                    drawY,
                    drawWidth,
                    drawHeight,
                    null);

            g2.setComposite(oldComposite);
        }

        board.draw(g2, gm.isBoardFlipped());

        // Draw highlighted move on the board depending on current view (live or history)
        int viewIndex = gm.getViewMoveIndex();
        int highlightMoveIndex = (viewIndex == -1) ? (gm.moves.isEmpty() ? -1 : gm.moves.size() - 1) : (viewIndex - 1);
        if (highlightMoveIndex >= 0 && highlightMoveIndex < gm.moves.size()) {
            com.jchess.util.MoveRecord lastMove = gm.moves.get(highlightMoveIndex);
            int fromCol = lastMove.fromCol;
            int fromRow = lastMove.fromRow;
            int toCol = lastMove.toCol;
            int toRow = lastMove.toRow;

            // Flip coordinates if board is flipped (since move history is in original coordinates)
            if (gm.isBoardFlipped()) {
                fromCol = 7 - fromCol;
                fromRow = 7 - fromRow;
                toCol = 7 - toCol;
                toRow = 7 - toRow;
            }

            g2.setColor(new Color(218, 224, 115, 100)); // Sleek semi-transparent yellow-green
            g2.fillRect(Board.ORIGIN_X + fromCol * Board.SIZE, Board.ORIGIN_Y + fromRow * Board.SIZE, Board.SIZE, Board.SIZE);
            g2.fillRect(Board.ORIGIN_X + toCol * Board.SIZE, Board.ORIGIN_Y + toRow * Board.SIZE, Board.SIZE, Board.SIZE);
        }

        // Highlight the selected piece's square
        if (gm.activeP != null && !gm.gameOver && !gm.stalemate) {
            g2.setColor(new Color(255, 255, 100, 120)); // Semi-transparent yellow highlight
            g2.fillRect(Board.ORIGIN_X + gm.activeP.col * Board.SIZE, Board.ORIGIN_Y + gm.activeP.row * Board.SIZE, Board.SIZE, Board.SIZE);
            
            // Draw a border around the selected square
            g2.setColor(new Color(255, 255, 150, 200));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(Board.ORIGIN_X + gm.activeP.col * Board.SIZE, Board.ORIGIN_Y + gm.activeP.row * Board.SIZE, Board.SIZE, Board.SIZE);
        }

        // Draw the side information panel
        g2.setColor(new Color(12, 14, 18, 176));
        g2.fillRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);
        g2.setColor(new Color(255, 255, 255, 48));
        g2.drawRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);

        if (isAnalysisMode) {
            drawEvaluationBar(g2);
        }

        // Highlight the king when it is in check
        drawCheckedKingGlow(g2);

        // Draw all pieces currently on the board (live or historical view)
        for (Piece p : new ArrayList<>(gm.getDisplayPieces())) {
            drawPieceWithFlip(g2, p);
        }

        // Mark squares that are legal moves for the piece
        if (gm.activeP != null && !gm.legalMoveSquares.isEmpty() && !gm.gameOver && !gm.stalemate) {
            for (Point square : gm.legalMoveSquares) {
                drawMoveDot(g2, square.x, square.y);
            }
        }

        // Draw the piece currently being dragged
        if (gm.activeP != null) {
            if (gm.canMove && gm.validSquare) {
                drawMoveDot(g2, gm.activeP.col, gm.activeP.row);
            }
            drawPieceWithFlip(g2, gm.activeP);
        }

        // Draw right-click annotations (highlights & arrows)
        drawRightClickHighlights(g2);
        drawRightClickArrows(g2);

        // Draw turn information and promotion options
        drawStatus(g2);

        // Draw the timer for both players
        drawTimer(g2);

        // Draw captured pieces tracker
        drawCapturedPieces(g2);

        // Draw the move log (only when not in promotion screen and game is not over)
        if (!gm.promotion && !gm.gameOver && !gm.stalemate) {
            moveLogRenderer.drawMoveLog(g2);
        }

        // Draw the in-panel dropdown menu if open
        drawMenuDropdown(g2);

        // Display the game result when the game ends
        drawGameResult(g2);

        updateActionCursor();
        
        // Show large centered pause indicator if timer is paused (Feature 37)
        if (timerPaused) {
            g2.setFont(new Font("Roboto", Font.BOLD, 48));
            g2.setColor(new Color(255, 200, 100));
            drawCenteredString(g2, "PAUSED", WIDTH / 2, HEIGHT / 2);
        }
    }

    // Draw the dot for the highlight, or a capture ring if the square has an enemy piece
    private void drawMoveDot(Graphics2D g2, int col, int row) {
        boolean isCapture = isCaptureSquare(col, row);

        boolean hovered = mouse.x >= Board.ORIGIN_X + col * Board.SIZE && mouse.x < Board.ORIGIN_X + (col + 1) * Board.SIZE
                   && mouse.y >= Board.ORIGIN_Y + row * Board.SIZE && mouse.y < Board.ORIGIN_Y + (row + 1) * Board.SIZE;

        Composite oldComposite = g2.getComposite();

        if (isCapture) {
            // Draw a circle tangent to the square (inscribed ring) for capture moves
            int margin = 5;
            int diameter = Board.SIZE - 2 * margin;
            double x = Board.ORIGIN_X + col * Board.SIZE + margin;
            double y = Board.ORIGIN_Y + row * Board.SIZE + margin;

            if (hovered) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f));
                g2.setColor(new Color(255, 255, 255));
                g2.setStroke(new BasicStroke(5f));
                g2.draw(new Ellipse2D.Double(x, y, diameter, diameter));
            } else {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(5f));
                g2.draw(new Ellipse2D.Double(x, y, diameter, diameter));
            }
        } else {
            // Regular move dot for non-capture moves
            double centerX = Board.ORIGIN_X + col * Board.SIZE + Board.SIZE / 2.0;
            double centerY = Board.ORIGIN_Y + row * Board.SIZE + Board.SIZE / 2.0;
            double outerR = MOVE_DOT_OUTER_SIZE / 2.0;
            double innerR = MOVE_DOT_INNER_SIZE / 2.0;

            if (hovered) {
                // Draw a brighter, more opaque highlight on hover
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f));
                g2.setColor(new Color(255, 255, 255));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new Ellipse2D.Double(centerX - outerR, centerY - outerR, MOVE_DOT_OUTER_SIZE, MOVE_DOT_OUTER_SIZE));

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                g2.setColor(new Color(255, 255, 255));
                g2.fill(new Ellipse2D.Double(centerX - innerR, centerY - innerR, MOVE_DOT_INNER_SIZE, MOVE_DOT_INNER_SIZE));
            } else {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new Ellipse2D.Double(centerX - outerR, centerY - outerR, MOVE_DOT_OUTER_SIZE, MOVE_DOT_OUTER_SIZE));

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2.fill(new Ellipse2D.Double(centerX - innerR, centerY - innerR, MOVE_DOT_INNER_SIZE, MOVE_DOT_INNER_SIZE));
            }
        }

        g2.setComposite(oldComposite);
    }

    // Check if the square contains an enemy piece on the actual board (i.e., this is a capture move)
    // Uses gm.pieces rather than simPieces because simPieces may have the enemy removed during drag simulation
    private boolean isCaptureSquare(int col, int row) {
        for (Piece p : gm.pieces) {
            if (p.col == col && p.row == row && p.color != gm.currentColor) {
                return true;
            }
        }
        return false;
    }

    // Highlight the king when it is in check with a pulsing glow effect
    private void drawCheckedKingGlow(Graphics2D g2) {
        if (gm.checkingP == null)
            return;
        Piece king = null;
        for (Piece p : gm.getDisplayPieces()) {
            if (p.type == PieceType.KING && p.color == gm.getOppositeColor(gm.checkingP.color)) {
                king = p;
                break;
            }
        }
        if (king == null)
            return;

        double centerX = Board.ORIGIN_X + king.col * Board.SIZE + Board.SIZE / 2.0;
        double centerY = Board.ORIGIN_Y + king.row * Board.SIZE + Board.SIZE / 2.0;
        Composite oldComposite = g2.getComposite();

        // Pulsing animation: oscillate between 0.6 and 1.0 based on time
        double pulse = 0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 300.0);

        // Outer glow layers with pulsing intensity
        for (int layer = 8; layer >= 1; layer--) {
            int diameter = Board.SIZE + layer * 12;
            double radius = diameter / 2.0;
            float alpha = (float) ((0.25f / 8) * layer * pulse);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(alpha, 0.35f)));
            g2.setColor(new Color(255, 60, 60));
            g2.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, diameter, diameter));
        }

        // Inner bright ring for emphasis
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * (float)pulse));
        g2.setColor(new Color(255, 100, 100));
        g2.setStroke(new BasicStroke(3f));
        int ringDiameter = Board.SIZE + 4;
        double ringRadius = ringDiameter / 2.0;
        g2.draw(new Ellipse2D.Double(centerX - ringRadius, centerY - ringRadius, ringDiameter, ringDiameter));

        g2.setComposite(oldComposite);
    }

    // Draw a piece with board flip transformation applied
    private void drawPieceWithFlip(Graphics2D g2, Piece p) {
        if (!gm.isBoardFlipped()) {
            p.draw(g2);
            return;
        }

        // Draw piece at its current (already flipped) position
        p.draw(g2);
    }

    // Draw turn information and promotion options
    private void drawStatus(Graphics2D g2) {
        if (gm.gameOver || gm.stalemate) {
            return;
        }

        g2.setColor(Color.white);

        if (gm.promotion) {
            g2.setFont(new Font("Roboto", Font.PLAIN, 22));
            drawCenteredString(g2, "Promote to:", SIDE_PANEL_CENTER_X, PROMOTION_LABEL_Y);
            for (Piece p : gm.promoPieces) {
                g2.drawImage(p.img, p.getX(p.col), p.getY(p.row), Board.SIZE, Board.SIZE, null);
            }
        } else {
            boolean isPlayerTurn = (gm.currentColor == GameManager.WHITE) == isPlayerWhite;
            boolean isFlipped = gm.isBoardFlipped();
            
            g2.setFont(new Font("Roboto", Font.BOLD, 18));
            
            String opponentName = isVsComputer ? "Stockfish (" + engineDifficulty.getDisplayName() + ")" : null;
            if (computerThinking && !isPlayerTurn) {
                opponentName = "Stockfish thinking...";
            }

            if (gm.currentColor == com.jchess.game.GameManager.WHITE) {
                // White's turn text goes to the side that has White pieces
                // When board is flipped, White pieces are at the top
                int turnY = isFlipped ? BLACK_TURN_Y - 10 : WHITE_TURN_Y + 25;
                int checkY = isFlipped ? BLACK_CHECK_Y - 15 : WHITE_CHECK_Y + 25;
                String turnText = isPlayerTurn ? "Your turn" : (opponentName != null ? opponentName : "White's turn");
                drawCenteredString(g2, turnText, SIDE_PANEL_CENTER_X - 65, turnY);
                if (gm.checkingP != null && gm.checkingP.color == com.jchess.game.GameManager.BLACK) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X - 60, checkY);
                }
            } else {
                // Black's turn text goes to the side that has Black pieces
                // When board is not flipped, Black pieces are at the top
                int turnY = isFlipped ? WHITE_TURN_Y + 25 : BLACK_TURN_Y - 10;
                int checkY = isFlipped ? WHITE_CHECK_Y + 25 : BLACK_CHECK_Y - 15;
                String turnText = isPlayerTurn ? "Your turn" : (opponentName != null ? opponentName : "Black's turn");
                drawCenteredString(g2, turnText, SIDE_PANEL_CENTER_X - 65, turnY);
                if (gm.checkingP != null && gm.checkingP.color == com.jchess.game.GameManager.WHITE) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X - 60, checkY);
                }
            }

            if (isAnalysisMode) {
                g2.setFont(new Font("Roboto", Font.PLAIN, 22));
                g2.setColor(new Color(215, 222, 230));
                String evalText = analysisThinking ? "Evaluating..." : "" + analysisEvaluationText;
                g2.drawString(evalText, SIDE_PANEL_X + 16, SIDE_PANEL_Y + 70);
            }
        }
    }

    private void drawEvaluationBar(Graphics2D g2) {
        int barX = EVAL_BAR_X;
        int barY = Board.ORIGIN_Y;
        int barWidth = EVAL_BAR_WIDTH;
        int barHeight = EVAL_BAR_HEIGHT;

        g2.setColor(new Color(14, 16, 20, 210));
        g2.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);

        int boundedScore = Math.max(-1000, Math.min(1000, analysisEvaluationWhiteScore));
        double whiteShare = (boundedScore + 1000.0) / 2000.0;
        int splitY = barY + (int) Math.round(barHeight * whiteShare);

        g2.setColor(new Color(235, 240, 235, 225));
        g2.fillRoundRect(barX + 1, barY + 1, barWidth - 2, Math.max(0, splitY - barY - 1), 5, 5);

        g2.setColor(new Color(92, 48, 48, 225));
        g2.fillRoundRect(barX + 1, splitY, barWidth - 2, Math.max(0, barHeight - (splitY - barY) - 1), 5, 5);

        g2.setColor(new Color(255, 255, 255, 55));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(barX, barY, barWidth, barHeight, 6, 6);

        g2.setColor(boundedScore >= 0 ? new Color(110, 205, 135, 220) : new Color(225, 95, 95, 220));
        g2.fillRect(barX + 1, Math.max(barY + 1, splitY - 1), barWidth - 2, 2);
    }

    // Display the game result when the game ends
    private void drawGameResult(Graphics2D g2) {
        if (!isGameFinished()) {
            return;
        }

        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.68f));
        g2.setColor(new Color(28, 30, 34));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(oldComposite);

        int panelWidth = 380;
        int panelHeight = 230;
        int panelX = (getWidth() - panelWidth) / 2;
        int panelY = (getHeight() - panelHeight) / 2 - 5;

        g2.setColor(new Color(12, 14, 18, 235));
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 18, 18);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 18, 18);

        int centerX = panelX + panelWidth / 2;
        int titleY = panelY + 70;
        int detailY = panelY + 110;

        String resultTitle;
        String resultDetail;

        if (gm.whiteResign) {
            resultTitle = "Black Wins";
            resultDetail = "by Resignation";
        } else if (gm.blackResign) {
            resultTitle = "White Wins";
            resultDetail = "by Resignation";
        } else if (gm.timeOutWinner != null) {
            resultTitle = (gm.timeOutWinner == com.jchess.game.GameManager.WHITE ? "White" : "Black") + " Wins";
            resultDetail = "by Time";
        } else if (gm.stalemate) {
            if (gm.drawByRepetition) {
                resultTitle = "Draw";
                resultDetail = "by Threefold Repetition";
            } else if (gm.drawByFiftyMove) {
                resultTitle = "Draw";
                resultDetail = "by 50-Move Rule";
            } else if (gm.getMoveValidator().isInsufficientMaterial()) {
                resultTitle = "Draw";
                resultDetail = "by Insufficient Material";
            } else {
                resultTitle = "Stalemate";
                resultDetail = "No legal moves available";
            }
        } else {
            // currentColor is the winner (the side that just delivered checkmate)
            resultTitle = (gm.currentColor == com.jchess.game.GameManager.WHITE) ? "White Wins" : "Black Wins";
            resultDetail = "Checkmate";
        }

        g2.setFont(new Font("Roboto", Font.BOLD, 32));
        g2.setColor(new Color(126, 255, 140));
        drawCenteredString(g2, resultTitle, centerX, titleY);

        g2.setFont(new Font("Roboto", Font.PLAIN, 22));
        g2.setColor(new Color(200, 200, 200));
        drawCenteredString(g2, resultDetail, centerX, detailY);

        int buttonWidth = 140;
        int buttonHeight = 40;
        int buttonY = panelY + panelHeight - 62;
        int buttonGap = 16;
        int restartX = centerX - buttonWidth - buttonGap / 2;
        int titleX = centerX + buttonGap / 2;

        drawEndButton(g2, restartButtonRect, restartX, buttonY, buttonWidth, buttonHeight,
                "Play again", new Color(70, 150, 230), new Color(94, 175, 255));
        drawEndButton(g2, titleButtonRect, titleX, buttonY, buttonWidth, buttonHeight,
                "Main Menu", new Color(82, 88, 98), new Color(110, 118, 128));
    }

    private void drawEndButton(Graphics2D g2, java.awt.Rectangle rect, int x, int y, int width, int height,
            String text, Color baseColor, Color hoverColor) {
        boolean hovered = rect.contains(mouse.x, mouse.y);
        boolean pressed = hovered && mouse.pressed;

        Color fill = pressed ? hoverColor.darker() : hovered ? hoverColor : baseColor;
        g2.setColor(fill);
        g2.fillRoundRect(x, y, width, height, 12, 12);
        g2.setColor(new Color(255, 255, 255, hovered ? 90 : 45));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, width, height, 12, 12);

        rect.setBounds(x, y, width, height);

        g2.setFont(new Font("Roboto", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        FontMetrics metrics = g2.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + (height + metrics.getAscent() - metrics.getDescent()) / 2 - 1;
        g2.drawString(text, textX, textY + (pressed ? 1 : 0));
    }

    // Inner class representing a right-click arrow annotation
    private static class Arrow {
        int startCol, startRow, endCol, endRow;
        Arrow(int startCol, int startRow, int endCol, int endRow) {
            this.startCol = startCol;
            this.startRow = startRow;
            this.endCol = endCol;
            this.endRow = endRow;
        }
    }

    // Update right-click highlights and arrows based on mouse state
    private void updateRightClickAnnotations() {
        boolean rightJustPressed = mouse.rightPressed && !rightPressedLastFrame;
        boolean rightJustReleased = !mouse.rightPressed && rightPressedLastFrame;
        rightPressedLastFrame = mouse.rightPressed;

        int mouseCol = (mouse.x - Board.ORIGIN_X) / Board.SIZE;
        int mouseRow = (mouse.y - Board.ORIGIN_Y) / Board.SIZE;
        int logicalCol = gm.isBoardFlipped() ? 7 - mouseCol : mouseCol;
        int logicalRow = gm.isBoardFlipped() ? 7 - mouseRow : mouseRow;

        // Validate that the mouse is on the board
        boolean onBoard = mouseCol >= 0 && mouseCol < 8 && mouseRow >= 0 && mouseRow < 8;

        // Left-click on the board clears all annotations
        boolean leftJustPressed = mouse.pressed && !leftPressedLastFrame;
        leftPressedLastFrame = mouse.pressed;
        if (leftJustPressed && onBoard) {
            rightClickHighlights.clear();
            rightClickArrows.clear();
            rightClickStartCol = -1;
            rightClickStartRow = -1;
            rightClickDragging = false;
        }

        if (rightJustPressed && onBoard) {
            rightClickStartCol = logicalCol;
            rightClickStartRow = logicalRow;
            rightClickDragging = true;
        }

        if (rightJustReleased) {
            if (rightClickDragging && rightClickStartCol >= 0 && rightClickStartRow >= 0) {
                if (onBoard && (logicalCol != rightClickStartCol || logicalRow != rightClickStartRow)) {
                    // Dragged to a different square — toggle arrow from start to end
                    boolean found = false;
                    for (int i = 0; i < rightClickArrows.size(); i++) {
                        Arrow a = rightClickArrows.get(i);
                        if (a.startCol == rightClickStartCol && a.startRow == rightClickStartRow
                            && a.endCol == logicalCol && a.endRow == logicalRow) {
                            rightClickArrows.remove(i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        rightClickArrows.add(new Arrow(rightClickStartCol, rightClickStartRow, logicalCol, logicalRow));
                    }
                } else {
                    // Right-click on same square (no drag) — toggle highlight circle
                    Point p = new Point(rightClickStartCol, rightClickStartRow);
                    boolean found = false;
                    for (int i = 0; i < rightClickHighlights.size(); i++) {
                        if (rightClickHighlights.get(i).equals(p)) {
                            rightClickHighlights.remove(i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        rightClickHighlights.add(p);
                    }
                }
            }
            rightClickStartCol = -1;
            rightClickStartRow = -1;
            rightClickDragging = false;
        }
    }

    // Draw all right-click highlight circles
    private void drawRightClickHighlights(Graphics2D g2) {
        int margin = 4;
        int diameter = Board.SIZE - 2 * margin;
        g2.setStroke(new BasicStroke(4));
        g2.setColor(new Color(92, 151, 98, 200));
        for (Point p : rightClickHighlights) {
            int drawCol = gm.isBoardFlipped() ? 7 - p.x : p.x;
            int drawRow = gm.isBoardFlipped() ? 7 - p.y : p.y;
            int x = Board.ORIGIN_X + drawCol * Board.SIZE + margin;
            int y = Board.ORIGIN_Y + drawRow * Board.SIZE + margin;
            g2.draw(new Ellipse2D.Double(x, y, diameter, diameter));
        }
    }

    // Draw all right-click arrows
    private void drawRightClickArrows(Graphics2D g2) {
        g2.setStroke(new BasicStroke(10.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(92, 151, 98, 200));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Arrow arrow : rightClickArrows) {
            int startCol = gm.isBoardFlipped() ? 7 - arrow.startCol : arrow.startCol;
            int startRow = gm.isBoardFlipped() ? 7 - arrow.startRow : arrow.startRow;
            int endCol = gm.isBoardFlipped() ? 7 - arrow.endCol : arrow.endCol;
            int endRow = gm.isBoardFlipped() ? 7 - arrow.endRow : arrow.endRow;
            double startX = Board.ORIGIN_X + startCol * Board.SIZE + Board.SIZE / 2.0;
            double startY = Board.ORIGIN_Y + startRow * Board.SIZE + Board.SIZE / 2.0;
            double endX = Board.ORIGIN_X + endCol * Board.SIZE + Board.SIZE / 2.0;
            double endY = Board.ORIGIN_Y + endRow * Board.SIZE + Board.SIZE / 2.0;

            double angle = Math.atan2(endY - startY, endX - startX);
            double arrowLength = 45;
            double arrowAngle = Math.toRadians(30);

            // Shorten line endpoint slightly so arrowhead sits at the tip
            double lineEndX = endX - arrowLength * 0.5 * Math.cos(angle);
            double lineEndY = endY - arrowLength * 0.5 * Math.sin(angle);

            // Draw the line
            g2.draw(new java.awt.geom.Line2D.Double(startX, startY, lineEndX, lineEndY));

            // Draw arrowhead at the actual endpoint
            double x1 = endX - arrowLength * Math.cos(angle - arrowAngle);
            double y1 = endY - arrowLength * Math.sin(angle - arrowAngle);
            double x2 = endX - arrowLength * Math.cos(angle + arrowAngle);
            double y2 = endY - arrowLength * Math.sin(angle + arrowAngle);

            int[] xPoints = {(int) endX, (int) x1, (int) x2};
            int[] yPoints = {(int) endY, (int) y1, (int) y2};
            g2.fillPolygon(xPoints, yPoints, 3);
        }
    }

    private void drawResignRed(int x, int y) {
        Graphics2D g2 = (Graphics2D) getGraphics();
        g2.setColor(new Color(255, 80, 80, 180));
        g2.fillOval(x - 15, y - 15, 30, 30);
    }

    private void updateTimer() {
        if (gm.gameOver || gm.stalemate || timerPaused) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSecondTimestamp >= 1000) {
            lastSecondTimestamp = currentTime;

            // In normal play, decrement the side whose turn it is (gm.currentColor).
            // When the human is playing BLACK, invert which clock is running.
            int runningColor = isPlayerWhite ? gm.currentColor : gm.getOppositeColor(gm.currentColor);

            if (runningColor == com.jchess.game.GameManager.WHITE) {
                whiteTimeRemaining--;
                if (whiteTimeRemaining <= 0) {
                    whiteTimeRemaining = 0;
                    gm.timeOutWin(com.jchess.game.GameManager.BLACK);
                }
            } else {
                blackTimeRemaining--;
                if (blackTimeRemaining <= 0) {
                    blackTimeRemaining = 0;
                    gm.timeOutWin(com.jchess.game.GameManager.WHITE);
                }
            }
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private void drawTimer(Graphics2D g2){
        int timerWidth = 120;
        int timerHeight = 40;
        int timerX = SIDE_PANEL_CENTER_X;
        int timerBlackY = SIDE_PANEL_Y + 15;
        int timerWhiteY = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT - timerHeight - 15;

        // Board flip only affects where pieces are *drawn*.
        // Clock values must be shown for the correct color at the correct location:
        // - Without flip: BLACK pieces at the top area, WHITE at the bottom.
        // - With flip: WHITE pieces at the top area, BLACK at the bottom.
        boolean isFlipped = gm.isBoardFlipped();

        int topTimerY = isFlipped ? timerWhiteY : timerBlackY;
        int bottomTimerY = isFlipped ? timerBlackY : timerWhiteY;

        int topColor = isFlipped ? GameManager.WHITE : GameManager.BLACK;
        int bottomColor = isFlipped ? GameManager.BLACK : GameManager.WHITE;

        // Draw timer backgrounds
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRoundRect(timerX, bottomTimerY, timerWidth, timerHeight, 4, 4);
        g2.fillRoundRect(timerX, topTimerY, timerWidth, timerHeight, 4, 4);

        // Draw timer text
        g2.setFont(new Font("Roboto", Font.BOLD, 16));
        g2.setColor(Color.white);

        FontMetrics metrics = g2.getFontMetrics();

        String topTime = formatTime(topColor == GameManager.WHITE ? whiteTimeRemaining : blackTimeRemaining);
        int topTextX = timerX + (timerWidth - metrics.stringWidth(topTime)) / 2;
        int topTextY = topTimerY + timerHeight / 2 + metrics.getHeight() / 2 - 3;
        g2.drawString(topTime, topTextX, topTextY);

        String bottomTime = formatTime(bottomColor == GameManager.WHITE ? whiteTimeRemaining : blackTimeRemaining);
        int bottomTextX = timerX + (timerWidth - metrics.stringWidth(bottomTime)) / 2;
        int bottomTextY = bottomTimerY + timerHeight / 2 + metrics.getHeight() / 2 - 3;
        g2.drawString(bottomTime, bottomTextX, bottomTextY);

        // Highlight active player's timer strictly by gm.currentColor
        if (!gm.gameOver && !gm.stalemate) {
            int activeColor = isPlayerWhite ? gm.currentColor : gm.getOppositeColor(gm.currentColor);
            boolean highlightTop = activeColor == topColor;
            int activeTimerY = highlightTop ? topTimerY : bottomTimerY;
            g2.setColor(new Color(255, 255, 255, 60));
            g2.setStroke(new java.awt.BasicStroke(2));
            g2.drawRoundRect(timerX - 2, activeTimerY - 2, timerWidth + 4, timerHeight + 4, 6, 6);
        }
    }

    // Draw text centered at a specified position
    private void drawCenteredString(Graphics2D g2, String text, int centerX, int baselineY) {
        FontMetrics metrics = g2.getFontMetrics();
        int textX = centerX - metrics.stringWidth(text) / 2;
        g2.drawString(text, textX, baselineY);
    }

    private void updateActionCursor() {
        boolean hovering = false;

        if (isGameFinished()) {
            hovering = restartButtonRect.contains(mouse.x, mouse.y)
                    || titleButtonRect.contains(mouse.x, mouse.y);
        } else {
            boolean canUndo = gm.canUndo();
            boolean canResign = !gm.gameOver && !gm.stalemate;
            boolean canGoStart = !gm.moves.isEmpty() && gm.getViewMoveIndex() != 0;
            boolean canGoPrev = !gm.moves.isEmpty() && (gm.getViewMoveIndex() > 0 || gm.getViewMoveIndex() == -1);
            boolean canGoNext = gm.getViewMoveIndex() != -1 && gm.getViewMoveIndex() < gm.moves.size();
            boolean canGoEnd = gm.getViewMoveIndex() != -1;

            hovering = menuButtonRect.contains(mouse.x, mouse.y)
                    || (canUndo && (undoWhiteRect.contains(mouse.x, mouse.y) || undoBlackRect.contains(mouse.x, mouse.y)))
                    || (canResign && (resignWhiteRect.contains(mouse.x, mouse.y) || resignBlackRect.contains(mouse.x, mouse.y)))
                    || (canGoStart && navStartRect.contains(mouse.x, mouse.y))
                    || (canGoPrev && navPrevRect.contains(mouse.x, mouse.y))
                    || (canGoNext && navNextRect.contains(mouse.x, mouse.y))
                    || (canGoEnd && navEndRect.contains(mouse.x, mouse.y));
        }

        setCursor(new java.awt.Cursor(hovering ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
    }

    /**
     * Toggles the in-panel dropdown menu open/closed.
     */
    private void toggleMenu() {
        menuOpen = !menuOpen;
    }

    /**
     * Closes the dropdown menu.
     */
    private void closeMenu() {
        menuOpen = false;
    }

    /**
     * Draws the dropdown menu below the Menu button if it is open.
     */
    private void drawMenuDropdown(Graphics2D g2) {
        if (!menuOpen) return;

        int itemHeight = 36;
        int dropdownWidth = 150;
        int dropdownX = menuButtonRect.x + menuButtonRect.width - dropdownWidth;
        int dropdownY = menuButtonRect.y + menuButtonRect.height + 4;

        // Background of the dropdown
        g2.setColor(new Color(20, 25, 35, 240));
        g2.fillRoundRect(dropdownX, dropdownY, dropdownWidth, itemHeight * 4, 6, 6);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(dropdownX, dropdownY, dropdownWidth, itemHeight * 4, 6, 6);

        // Set the dropdown area rect for click detection
        menuDropdownRect.setBounds(dropdownX, dropdownY, dropdownWidth, itemHeight * 4);

        // Item 0: Flip Board
        menuFlipRect.setBounds(dropdownX, dropdownY, dropdownWidth, itemHeight);
        boolean hoverFlip = menuFlipRect.contains(mouse.x, mouse.y);
        if (hoverFlip) {
            g2.setColor(new Color(52, 98, 155, 200));
            g2.fillRoundRect(dropdownX + 2, dropdownY + 2, dropdownWidth - 4, itemHeight - 2, 4, 4);
        }
        g2.setFont(new Font("Roboto", Font.PLAIN, 14));
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("Flip Board", dropdownX + 8, dropdownY + itemHeight / 2 + fm.getAscent() / 2 - 1);

        // Item 1: Settings
        menuSettingsRect.setBounds(dropdownX, dropdownY + itemHeight, dropdownWidth, itemHeight);
        boolean hoverSettings = menuSettingsRect.contains(mouse.x, mouse.y);
        if (hoverSettings) {
            g2.setColor(new Color(52, 98, 155, 200));
            g2.fillRoundRect(dropdownX + 2, dropdownY + itemHeight + 2, dropdownWidth - 4, itemHeight - 2, 4, 4);
        }
        g2.setColor(Color.WHITE);
        g2.drawString("Settings", dropdownX + 8, dropdownY + itemHeight + itemHeight / 2 + fm.getAscent() / 2 - 2);

        // Item 2: Show PGN
        menuPgnRect.setBounds(dropdownX, dropdownY + itemHeight * 2, dropdownWidth, itemHeight);
        boolean hoverPgn = menuPgnRect.contains(mouse.x, mouse.y);
        if (hoverPgn) {
            g2.setColor(new Color(52, 98, 155, 200));
            g2.fillRoundRect(dropdownX + 2, dropdownY + itemHeight * 2 + 2, dropdownWidth - 4, itemHeight - 2, 4, 4);
        }
        g2.setColor(Color.WHITE);
        g2.drawString("Show PGN", dropdownX + 8, dropdownY + itemHeight * 2 + itemHeight / 2 + fm.getAscent() / 2 - 2);

        // Item 3: Show FEN
        menuFenRect.setBounds(dropdownX, dropdownY + itemHeight * 3, dropdownWidth, itemHeight);
        boolean hoverFen = menuFenRect.contains(mouse.x, mouse.y);
        if (hoverFen) {
            g2.setColor(new Color(52, 98, 155, 200));
            g2.fillRoundRect(dropdownX + 2, dropdownY + itemHeight * 3 + 2, dropdownWidth - 4, itemHeight - 2, 4, 4);
        }
        g2.setColor(Color.WHITE);
        g2.drawString("Show FEN", dropdownX + 8, dropdownY + itemHeight * 3 + itemHeight / 2 + fm.getAscent() / 2 - 2);
    }

    public void applySettings() {
        gm.refreshPieceImages();
        repaint();
    }

    private void showSettingsDialog() {
        GameSettingsDialog.show(this, this);
    }

    private void showPgnDialog() {
        String pgn = gm.getPGN();

        JTextField pgnField = new JTextField(pgn);
        pgnField.setEditable(false);
        pgnField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        pgnField.setCaretPosition(0);
        pgnField.selectAll();

        JLabel hintLabel = new JLabel("Copy this PGN string:");
        hintLabel.setForeground(Color.DARK_GRAY);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.add(hintLabel);
        content.add(pgnField);

        JOptionPane.showMessageDialog(
                this,
                content,
                "Game PGN",
                JOptionPane.PLAIN_MESSAGE);

        pgnField.requestFocusInWindow();
        pgnField.selectAll();
    }

    private void showFenDialog() {
        String fen = gm.getFEN();

        JTextField fenField = new JTextField(fen);
        fenField.setEditable(false);
        fenField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        fenField.setCaretPosition(0);
        fenField.selectAll();

        JLabel hintLabel = new JLabel("Copy this FEN string:");
        hintLabel.setForeground(Color.DARK_GRAY);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.add(hintLabel);
        content.add(fenField);

        JOptionPane.showMessageDialog(
                this,
                content,
                "Current Board FEN",
                JOptionPane.PLAIN_MESSAGE);

        fenField.requestFocusInWindow();
        fenField.selectAll();
    }

    // Draw the captured pieces for both players in the side panel
    private void drawCapturedPieces(Graphics2D g2) {
        ArrayList<Piece> capturedWhite = gm.getSortedCapturedPieces(com.jchess.game.GameManager.WHITE);
        ArrayList<Piece> capturedBlack = gm.getSortedCapturedPieces(com.jchess.game.GameManager.BLACK);

        // When board is flipped (player is black), swap positions so captured pieces appear next to the correct side's area
        boolean isFlipped = gm.isBoardFlipped();
        int topY = SIDE_PANEL_Y + 80;
        int bottomY = 455;

        int topEndX, bottomEndX;
        if (isFlipped) {
            // White pieces are at the top when flipped, so black's captured pieces go on top
            topEndX = drawCapturedList(g2, capturedBlack, SIDE_PANEL_X + 16, topY);
            bottomEndX = drawCapturedList(g2, capturedWhite, SIDE_PANEL_X + 16, bottomY);
        } else {
            topEndX = drawCapturedList(g2, capturedWhite, SIDE_PANEL_X + 16, topY);
            bottomEndX = drawCapturedList(g2, capturedBlack, SIDE_PANEL_X + 16, bottomY);
        }

        int valWhite = gm.getCapturedValueByWhite();
        int valBlack = gm.getCapturedValueByBlack();

        g2.setFont(new Font("Roboto", Font.BOLD, 12));

        // Display the material advantage as a positive number next to the player who is ahead
        // When flipped, the captured piece positions are swapped, so the value text follows
        if (valWhite > valBlack) {
            g2.setColor(new Color(210, 215, 225));
            g2.drawString("+" + (valWhite - valBlack), isFlipped ? topEndX + 8 : bottomEndX + 8, isFlipped ? topY + 15 : bottomY + 15);
        } else if (valBlack > valWhite) {
            g2.setColor(new Color(210, 215, 225));
            g2.drawString("+" + (valBlack - valWhite), isFlipped ? bottomEndX + 8 : topEndX + 8, isFlipped ? bottomY + 15 : topY + 15);
        }
    }

    private void updateAnalysisEvaluation() {
        if (!isAnalysisMode || gm.gameOver || gm.stalemate || gm.promotion || gm.hasAnimations() || analysisThinking) {
            return;
        }

        String fen = gm.getFEN();
        if (fen.equals(lastAnalysisFen)) {
            return;
        }

        if (!ensureStockfishEngine(false)) {
            analysisEvaluationText = "Engine unavailable";
            analysisEvaluationWhiteScore = 0;
            lastAnalysisFen = fen;
            return;
        }

        analysisThinking = true;
        final String fenSnapshot = fen;
        final boolean whiteToMoveSnapshot = gm.currentColor == GameManager.WHITE;

        new Thread(() -> {
            try {
                com.jchess.util.StockfishEngine.Evaluation evaluation = stockfishEngine.getEvaluation(fenSnapshot, ANALYSIS_SEARCH_TIME_MS);
                final String displayText;
                final int whiteScore;

                if (evaluation == null) {
                    displayText = "N/A";
                    whiteScore = 0;
                } else {
                    displayText = evaluation.toDisplayString(whiteToMoveSnapshot);
                    whiteScore = evaluation.toWhiteCentipawns(whiteToMoveSnapshot);
                }

                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (fenSnapshot.equals(gm.getFEN())) {
                        analysisEvaluationText = displayText;
                        analysisEvaluationWhiteScore = whiteScore;
                        lastAnalysisFen = fenSnapshot;
                    }
                    analysisThinking = false;
                    repaint();
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    analysisEvaluationText = "N/A";
                    analysisEvaluationWhiteScore = 0;
                    analysisThinking = false;
                    repaint();
                });
            }
        }, "analysis-eval").start();
    }

    private boolean ensureStockfishEngine(boolean promptOnFailure) {
        if (stockfishEngine != null) {
            return true;
        }

        String path = com.jchess.util.StockfishEngine.getSavedPath();
        stockfishEngine = new com.jchess.util.StockfishEngine(path);
        if (stockfishEngine.start()) {
            return true;
        }

        stockfishEngine = null;
        if (promptOnFailure) {
            configureStockfishPath();
            return stockfishEngine != null;
        }

        return false;
    }

    // Helper method to draw a list of captured pieces in a row, returns the x-coordinate after the last drawn piece
    private int drawCapturedList(Graphics2D g2, ArrayList<Piece> piecesList, int startX, int startY) {
        int currentX = startX;
        int iconSize = 20;
        PieceType prevType = null;
        for (Piece p : piecesList) {
            if (p.img != null) {
                if (prevType != null) {
                    if (p.type == prevType) {
                        currentX += 8;
                    } else {
                        currentX += 22;
                    }
                }
                g2.drawImage(p.img, currentX, startY, iconSize, iconSize, null);
                prevType = p.type;
            }
        }
        return prevType == null ? startX : currentX + iconSize;
    }

    private void triggerComputerMove() {
        // Vs-computer guard: ensure only one engine request runs at a time.
        if (computerThinking) {
            return;
        }
        if (gm.gameOver || gm.stalemate) {
            return;
        }

        System.out.println("[Stockfish] triggerComputerMove called, currentColor=" + gm.currentColor + " playerColor=" + gm.getPlayerColor());
        computerThinking = true;
        new Thread(() -> {
            try {
                if (stockfishEngine == null) {
                    String path = com.jchess.util.StockfishEngine.getSavedPath();
                    System.out.println("[Stockfish] Creating engine with path: " + path);
                    stockfishEngine = new com.jchess.util.StockfishEngine(path);
                    if (!stockfishEngine.start()) {
                        System.err.println("[Stockfish] Engine start() returned false");
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            configureStockfishPath();
                            computerThinking = false;
                        });
                        return;
                    }
                    System.out.println("[Stockfish] Engine started successfully");
                }

                String fen = gm.getFEN();
                int skillLevel = engineDifficulty.getSkillLevel();
                int moveTime = engineDifficulty.getMoveTimeMs();
                stockfishEngine.setSkillLevel(skillLevel);
                System.out.println("[Stockfish] Querying bestMove for fen=" + fen + " (Difficulty=" + engineDifficulty.getDisplayName() + ", SkillLevel=" + skillLevel + ", movetime=" + moveTime + "ms)");
                String bestMove = stockfishEngine.getBestMove(fen, moveTime);
                System.out.println("[Stockfish] bestMove=" + bestMove);

                if (bestMove != null && !bestMove.equals("(none)")) {
                    final String bm = bestMove;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        applyComputerMove(bm);
                        computerThinking = false;
                    });
                } else {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        computerThinking = false;
                    });
                }
            } catch (Exception ex) {
                System.err.println("[Stockfish] Exception in computer move thread:");
                ex.printStackTrace();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    computerThinking = false;
                });
            }
        }).start();
    }

    private void configureStockfishPath() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Select Stockfish Executable Binary");
        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File fileToOpen = fileChooser.getSelectedFile();
            String path = fileToOpen.getAbsolutePath();
            com.jchess.util.StockfishEngine.savePath(path);
            
            stockfishEngine = new com.jchess.util.StockfishEngine(path);
            if (!stockfishEngine.start()) {
                JOptionPane.showMessageDialog(this, "Failed to start Stockfish at selected path: " + path, "Error", JOptionPane.ERROR_MESSAGE);
                stockfishEngine = null;
            }
        }
    }

    private void applyComputerMove(String bestMove) {
        if (bestMove.length() < 4) return;
        
        int fromAbsCol = bestMove.charAt(0) - 'a';
        int fromAbsRow = 8 - (bestMove.charAt(1) - '0');
        int toAbsCol = bestMove.charAt(2) - 'a';
        int toAbsRow = 8 - (bestMove.charAt(3) - '0');
        
        PieceType promoType = null;
        if (bestMove.length() == 5) {
            char p = bestMove.charAt(4);
            if (p == 'q') promoType = PieceType.QUEEN;
            else if (p == 'r') promoType = PieceType.ROOK;
            else if (p == 'b') promoType = PieceType.BISHOP;
            else if (p == 'n') promoType = PieceType.KNIGHT;
        }
        
        int fromCol = gm.boardFlipped ? 7 - fromAbsCol : fromAbsCol;
        int fromRow = gm.boardFlipped ? 7 - fromAbsRow : fromAbsRow;
        int toCol = gm.boardFlipped ? 7 - toAbsCol : toAbsCol;
        int toRow = gm.boardFlipped ? 7 - toAbsRow : toAbsRow;

        // Safety: if we can't find a piece at from-square, skip applying.
        // (Often indicates a FEN/coordinate mismatch.)
        com.jchess.model.Piece p = null;
        for (com.jchess.model.Piece piece : gm.pieces) {
            if (piece.col == fromCol && piece.row == fromRow) {
                p = piece;
                break;
            }
        }
        if (p == null) {
            System.err.println("Stockfish move rejected: no piece at from-square. bestMove=" + bestMove + " fen=" + gm.getFEN());
            return;
        }

        gm.makeValidatedEngineMove(fromCol, fromRow, toCol, toRow, promoType);

        repaint();
    }
}
