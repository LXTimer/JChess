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
    private static final int SIDE_PANEL_X = BOARD_PIXEL_SIZE + 12;
    private static final int SIDE_PANEL_Y = 45;
    private static final int SIDE_PANEL_WIDTH = WIDTH - SIDE_PANEL_X - 16;
    private static final int SIDE_PANEL_HEIGHT = 510;
    private static final int SIDE_PANEL_CENTER_X = SIDE_PANEL_X + SIDE_PANEL_WIDTH / 2;
    private static final int BLACK_TURN_Y = SIDE_PANEL_Y + 50;
    private static final int BLACK_CHECK_Y = BLACK_TURN_Y + 30;
    private static final int WHITE_TURN_Y = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT - 50;
    private static final int WHITE_CHECK_Y = WHITE_TURN_Y - 30;
    private static final int PROMOTION_LABEL_Y = SIDE_PANEL_Y + 110;
    private static final int MOVE_DOT_OUTER_SIZE = 30;
    private static final int MOVE_DOT_INNER_SIZE = 15;
    private static final float BACKGROUND_ALPHA = 0.70f;

    private Timer gameTimer;
    private final Board board;
    private final Mouse mouse;
    private final GameManager gm;
    private BufferedImage background;
    private int whiteTimeRemaining; // in seconds
    private int blackTimeRemaining; // in seconds
    private long lastSecondTimestamp;
    private boolean timerPaused = false;
    private long pauseStartTime = 0;
    private static final int INITIAL_TIME_SECONDS = 6000;
    private BufferedImage flipBoardIcon;
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
    private java.awt.Rectangle flipButtonRect = new java.awt.Rectangle();
    private java.awt.Rectangle resignWhiteRect = new java.awt.Rectangle();
    private java.awt.Rectangle resignBlackRect = new java.awt.Rectangle();
    private java.awt.Rectangle undoWhiteRect = new java.awt.Rectangle();
    private java.awt.Rectangle undoBlackRect = new java.awt.Rectangle();
    private java.awt.Rectangle fenButtonRect = new java.awt.Rectangle();
    private java.awt.Rectangle navStartRect  = new java.awt.Rectangle(); // |<  go to start
    private java.awt.Rectangle navPrevRect   = new java.awt.Rectangle(); // <   go back one move
    private java.awt.Rectangle navNextRect   = new java.awt.Rectangle(); // >   go forward one move
    private java.awt.Rectangle navEndRect    = new java.awt.Rectangle(); // >|  go to end (live)
    private java.awt.Rectangle restartButtonRect = new java.awt.Rectangle();
    private java.awt.Rectangle titleButtonRect = new java.awt.Rectangle();
    private boolean isPlayerWhite = true;
    private TitlePanel titlePanel;
    private int lastInitialTimeSeconds = INITIAL_TIME_SECONDS;

    // Constructor
    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.black);
        setDoubleBuffered(true);

        mouse = new Mouse();
        board = new Board();
        gm = new com.jchess.game.GameManager(mouse);
        background = loadBackground();
        flipBoardIcon = loadFlipBoardIcon();
        ResignIcon = loadResignIcon();
        undoIcon = loadUndoIcon();
        moveLogRenderer = new GamePanelMoveLogRenderer(gm, mouse, flipBoardIcon, ResignIcon, undoIcon,
            flipButtonRect, resignWhiteRect, resignBlackRect, undoWhiteRect, undoBlackRect, fenButtonRect,
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
        setVisible(false);
        if (titlePanel != null) {
            titlePanel.showTitlePanel();
        }
        repaint();
    }

    private void resetMatch(int initialTimeSeconds) {
        gm.resetGameState();

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
                } else if (mouseJustPressed && fenButtonRect.contains(mouse.x, mouse.y)) {
                    showFenDialog();
                } else if (mouseJustPressed && flipButtonRect.contains(mouse.x, mouse.y)) {
                    gm.toggleFlipBoard();
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
                } else {
                    gm.update(mouseJustPressed, mouseJustReleased);
                }

                updateRightClickAnnotations();
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
        if (fenButtonRect.contains(mouse.x, mouse.y)) {
            showFenDialog();
            return true;
        }

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

    // Helper method for loading the flip board icon
    private BufferedImage loadFlipBoardIcon() {
        try (InputStream in = GamePanel.class.getResourceAsStream("/com/jchess/resources/icons/flip_board.png")) {
            if (in == null) {
                System.err.println("Failed to load image: /resources/icons/flip_board.png");
                return null;
            }
            return scaleImage(ImageIO.read(in), 14, 14);
        } catch (Exception e) {
            System.err.println("Failed to load image: /resources/icons/flip_board.png");
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
        if (background == null) {

            g2.setColor(new Color(20, 21, 24));
            g2.fillRect(0, 0, getWidth(), getHeight());

        } else {

            Composite oldComposite = g2.getComposite();

            g2.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            BACKGROUND_ALPHA));

            double scale = Math.max(
                    (double) getWidth() / background.getWidth(),
                    (double) getHeight() / background.getHeight());

            int drawWidth = (int) (background.getWidth() * scale);
            int drawHeight = (int) (background.getHeight() * scale);

            int drawX = (getWidth() - drawWidth) / 2;
            int drawY = (getHeight() - drawHeight) / 2;

            g2.drawImage(
                    background,
                    drawX,
                    drawY,
                    drawWidth,
                    drawHeight,
                    null);

            g2.setComposite(oldComposite);
        }

        board.draw(g2);

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
            g2.fillRect(fromCol * Board.SIZE, fromRow * Board.SIZE, Board.SIZE, Board.SIZE);
            g2.fillRect(toCol * Board.SIZE, toRow * Board.SIZE, Board.SIZE, Board.SIZE);
        }

        // Draw the side information panel
        g2.setColor(new Color(12, 14, 18, 176));
        g2.fillRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);
        g2.setColor(new Color(255, 255, 255, 48));
        g2.drawRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);

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

        // Display the game result when the game ends
        drawGameResult(g2);

        updateActionCursor();
    }

    // Draw the dot for the highlight, or a capture ring if the square has an enemy piece
    private void drawMoveDot(Graphics2D g2, int col, int row) {
        boolean isCapture = isCaptureSquare(col, row);

        boolean hovered = mouse.x >= col * Board.SIZE && mouse.x < (col + 1) * Board.SIZE
                       && mouse.y >= row * Board.SIZE && mouse.y < (row + 1) * Board.SIZE;

        Composite oldComposite = g2.getComposite();

        if (isCapture) {
            // Draw a circle tangent to the square (inscribed ring) for capture moves
            int margin = 5;
            int diameter = Board.SIZE - 2 * margin;
            double x = col * Board.SIZE + margin;
            double y = row * Board.SIZE + margin;

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
            double centerX = col * Board.SIZE + Board.SIZE / 2.0;
            double centerY = row * Board.SIZE + Board.SIZE / 2.0;
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

    // Highlight the king when it is in check
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

        double centerX = king.col * Board.SIZE + Board.SIZE / 2.0;
        double centerY = king.row * Board.SIZE + Board.SIZE / 2.0;
        Composite oldComposite = g2.getComposite();

        for (int layer = 6; layer >= 1; layer--) {
            int diameter = Board.SIZE + layer * 10;
            double radius = diameter / 2.0;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (0.2f / 6) * layer));
            g2.setColor(new Color(255, 80, 80));
            g2.fill(new Ellipse2D.Double(centerX - radius, centerY - radius, diameter, diameter));
        }

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
            
            if (gm.currentColor == com.jchess.game.GameManager.WHITE) {
                // White's turn text goes to the side that has White pieces
                // When board is flipped, White pieces are at the top
                int turnY = isFlipped ? BLACK_TURN_Y - 10 : WHITE_TURN_Y + 25;
                int checkY = isFlipped ? BLACK_CHECK_Y - 15 : WHITE_CHECK_Y + 25;
                String turnText = isPlayerTurn ? "Your turn" : "White's turn";
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
                String turnText = isPlayerTurn ? "Your turn" : "Black's turn";
                drawCenteredString(g2, turnText, SIDE_PANEL_CENTER_X - 65, turnY);
                if (gm.checkingP != null && gm.checkingP.color == com.jchess.game.GameManager.WHITE) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X - 60, checkY);
                }
            }
        }
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
            resultTitle = "Stalemate";
            resultDetail = gm.getMoveValidator().isInsufficientMaterial() ? "by Insufficient Material" : "No legal moves available";
        } else {
            resultTitle = (gm.currentColor == com.jchess.game.GameManager.WHITE) ? "Black Wins" : "White Wins";
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

        int mouseCol = mouse.x / Board.SIZE;
        int mouseRow = mouse.y / Board.SIZE;

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
            rightClickStartCol = mouseCol;
            rightClickStartRow = mouseRow;
            rightClickDragging = true;
        }

        if (rightJustReleased) {
            if (rightClickDragging && rightClickStartCol >= 0 && rightClickStartRow >= 0) {
                if (onBoard && (mouseCol != rightClickStartCol || mouseRow != rightClickStartRow)) {
                    // Dragged to a different square — toggle arrow from start to end
                    boolean found = false;
                    for (int i = 0; i < rightClickArrows.size(); i++) {
                        Arrow a = rightClickArrows.get(i);
                        if (a.startCol == rightClickStartCol && a.startRow == rightClickStartRow
                            && a.endCol == mouseCol && a.endRow == mouseRow) {
                            rightClickArrows.remove(i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        rightClickArrows.add(new Arrow(rightClickStartCol, rightClickStartRow, mouseCol, mouseRow));
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
            int x = p.x * Board.SIZE + margin;
            int y = p.y * Board.SIZE + margin;
            g2.draw(new Ellipse2D.Double(x, y, diameter, diameter));
        }
    }

    // Draw all right-click arrows
    private void drawRightClickArrows(Graphics2D g2) {
        g2.setStroke(new BasicStroke(10.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(92, 151, 98, 200));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Arrow arrow : rightClickArrows) {
            double startX = arrow.startCol * Board.SIZE + Board.SIZE / 2.0;
            double startY = arrow.startRow * Board.SIZE + Board.SIZE / 2.0;
            double endX = arrow.endCol * Board.SIZE + Board.SIZE / 2.0;
            double endY = arrow.endRow * Board.SIZE + Board.SIZE / 2.0;

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
        if (gm.gameOver || gm.stalemate) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSecondTimestamp >= 1000) {
            lastSecondTimestamp = currentTime;

            if (gm.currentColor == com.jchess.game.GameManager.WHITE) {
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

        // Determine which timer goes where based on board flip
        boolean isFlipped = gm.isBoardFlipped();
        int topTimerY = isFlipped ? timerWhiteY : timerBlackY;     // top timer
        int bottomTimerY = isFlipped ? timerBlackY : timerWhiteY;  // bottom timer
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

        // Bottom timer (player at bottom side)
        String bottomTime = formatTime(bottomColor == GameManager.WHITE ? whiteTimeRemaining : blackTimeRemaining);
        int bottomTextX = timerX + (timerWidth - metrics.stringWidth(bottomTime)) / 2;
        int bottomTextY = bottomTimerY + timerHeight / 2 + metrics.getHeight() / 2 - 3;
        g2.drawString(bottomTime, bottomTextX, bottomTextY);

        // Top timer (player at top side)
        String topTime = formatTime(topColor == GameManager.WHITE ? whiteTimeRemaining : blackTimeRemaining);
        int topTextX = timerX + (timerWidth - metrics.stringWidth(topTime)) / 2;
        int topTextY = topTimerY + timerHeight / 2 + metrics.getHeight() / 2 - 3;
        g2.drawString(topTime, topTextX, topTextY);

        // Show pause indicator if timer is paused (Feature 37)
        if (timerPaused) {
            g2.setFont(new Font("Roboto", Font.BOLD, 14));
            g2.setColor(new Color(255, 200, 100));
            String pausedText = "⏸ PAUSED";
            int pausedX = timerX + (timerWidth - metrics.stringWidth(pausedText)) / 2;
            int pausedY = (topTimerY + bottomTimerY + timerHeight) / 2 - 20;
            g2.drawString(pausedText, pausedX, pausedY);
        }
        
        // Highlight active player's timer
        if (!gm.gameOver && !gm.stalemate) {
            int activeTimerY;
            if (gm.currentColor == bottomColor) {
                activeTimerY = bottomTimerY;
            } else {
                activeTimerY = topTimerY;
            }
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
            hovering = fenButtonRect.contains(mouse.x, mouse.y)
                || restartButtonRect.contains(mouse.x, mouse.y)
                    || titleButtonRect.contains(mouse.x, mouse.y);
        } else {
            boolean canUndo = gm.canUndo();
            boolean canResign = !gm.gameOver && !gm.stalemate;
            boolean canGoStart = !gm.moves.isEmpty() && gm.getViewMoveIndex() != 0;
            boolean canGoPrev = !gm.moves.isEmpty() && (gm.getViewMoveIndex() > 0 || gm.getViewMoveIndex() == -1);
            boolean canGoNext = gm.getViewMoveIndex() != -1 && gm.getViewMoveIndex() < gm.moves.size();
            boolean canGoEnd = gm.getViewMoveIndex() != -1;

            hovering = fenButtonRect.contains(mouse.x, mouse.y)
                    || flipButtonRect.contains(mouse.x, mouse.y)
                    || (canUndo && (undoWhiteRect.contains(mouse.x, mouse.y) || undoBlackRect.contains(mouse.x, mouse.y)))
                    || (canResign && (resignWhiteRect.contains(mouse.x, mouse.y) || resignBlackRect.contains(mouse.x, mouse.y)))
                    || (canGoStart && navStartRect.contains(mouse.x, mouse.y))
                    || (canGoPrev && navPrevRect.contains(mouse.x, mouse.y))
                    || (canGoNext && navNextRect.contains(mouse.x, mouse.y))
                    || (canGoEnd && navEndRect.contains(mouse.x, mouse.y));
        }

        setCursor(new java.awt.Cursor(hovering ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
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
        int topY = SIDE_PANEL_Y + 60;
        int topEndX = drawCapturedList(g2, capturedWhite, SIDE_PANEL_X + 16, topY);

        ArrayList<Piece> capturedBlack = gm.getSortedCapturedPieces(com.jchess.game.GameManager.BLACK);
        int bottomY = 455;
        int bottomEndX = drawCapturedList(g2, capturedBlack, SIDE_PANEL_X + 16, bottomY);

        int valWhite = gm.getCapturedValueByWhite();
        int valBlack = gm.getCapturedValueByBlack();

        g2.setFont(new Font("Roboto", Font.BOLD, 12));

        // Display the material advantage as a positive number next to the player who is ahead
        if (valWhite > valBlack) {
            g2.setColor(new Color(210, 215, 225));
            g2.drawString("+" + (valWhite - valBlack), bottomEndX + 8, bottomY + 15);
        } else if (valBlack > valWhite) {
            g2.setColor(new Color(210, 215, 225));
            g2.drawString("+" + (valBlack - valWhite), topEndX + 8, topY + 15);
        }
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
}