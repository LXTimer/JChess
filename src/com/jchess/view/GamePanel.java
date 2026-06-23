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
import javax.swing.Timer;

import com.jchess.input.Mouse;
import com.jchess.model.Board;
import com.jchess.model.Piece;
import com.jchess.model.piece.PieceType;
import com.jchess.game.GameManager;
import com.jchess.util.MoveRecord;

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
    private static final int INITIAL_TIME_SECONDS = 6000;
    private BufferedImage flipBoardIcon;
    private BufferedImage ResignIcon;
    private BufferedImage undoIcon;
    private boolean mousePressedLastFrame = false;
    private java.awt.Rectangle flipButtonRect = new java.awt.Rectangle();
    private java.awt.Rectangle resignWhiteRect = new java.awt.Rectangle();
    private java.awt.Rectangle resignBlackRect = new java.awt.Rectangle();
    private java.awt.Rectangle undoWhiteRect = new java.awt.Rectangle();
    private java.awt.Rectangle undoBlackRect = new java.awt.Rectangle();
    private java.awt.Rectangle navStartRect  = new java.awt.Rectangle(); // |<  go to start
    private java.awt.Rectangle navPrevRect   = new java.awt.Rectangle(); // <   go back one move
    private java.awt.Rectangle navNextRect   = new java.awt.Rectangle(); // >   go forward one move
    private java.awt.Rectangle navEndRect    = new java.awt.Rectangle(); // >|  go to end (live)

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
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
        addMouseWheelListener(e -> {
            int notches = e.getWheelRotation();
            gm.scrollMoveLog(notches);
            repaint();
        });
    }

    // Start method
    public void startGame(int initialTimeSeconds) {
        if (gameTimer != null && gameTimer.isRunning()) {
            return;
        }

        // Initialize timer
        whiteTimeRemaining = initialTimeSeconds;
        blackTimeRemaining = initialTimeSeconds;
        lastSecondTimestamp = System.currentTimeMillis();

        gameTimer = new Timer(TIMER_DELAY, e -> {
            boolean mouseJustPressed = mouse.pressed && !mousePressedLastFrame;
            boolean mouseJustReleased = !mouse.pressed && mousePressedLastFrame;
            mousePressedLastFrame = mouse.pressed;

            // Check for button clicks
            if (mouseJustPressed && flipButtonRect.contains(mouse.x, mouse.y)) {
                gm.toggleFlipBoard();
            } else if (mouseJustPressed && undoWhiteRect.contains(mouse.x, mouse.y) && gm.canUndo()) {
                gm.undoLastMove();
            } else if (mouseJustPressed && undoBlackRect.contains(mouse.x, mouse.y) && gm.canUndo()) {
                gm.undoLastMove();
            } else if (mouseJustPressed && navStartRect.contains(mouse.x, mouse.y)) {
                // |<  jump to the starting position
                gm.viewStart();
            } else if (mouseJustPressed && navPrevRect.contains(mouse.x, mouse.y)) {
                // <   step back one move
                gm.viewPrevious();
            } else if (mouseJustPressed && navNextRect.contains(mouse.x, mouse.y)) {
                // >   step forward one move
                gm.viewNext();
            } else if (mouseJustPressed && navEndRect.contains(mouse.x, mouse.y)) {
                // >|  jump to the live / current position
                gm.viewEnd();
            } else if (mouseJustPressed && resignWhiteRect.contains(mouse.x, mouse.y) && !gm.gameOver) {
                gm.resign(0);
            } else if (mouseJustPressed && resignBlackRect.contains(mouse.x, mouse.y) && !gm.gameOver) {
                gm.resign(1);
            } else {
                // Main game loop update method
                gm.update(mouseJustPressed, mouseJustReleased);
            }

            // Update timer
            updateTimer();

            repaint();
        });
        gameTimer.setCoalesce(false);
        gameTimer.start();
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

        // Draw turn information and promotion options
        drawStatus(g2);

        // Draw the timer for both players
        drawTimer(g2);

        // Draw captured pieces tracker
        drawCapturedPieces(g2);

        // Draw the move log (only when not in promotion screen and game is not over)
        if (!gm.promotion && !gm.gameOver && !gm.stalemate) {
            drawMoveLog(g2);
        }

        // Display the game result when the game ends
        drawGameResult(g2);
    }

    // Draw the dot for the highlight
    private void drawMoveDot(Graphics2D g2, int col, int row) {
        double centerX = col * Board.SIZE + Board.SIZE / 2.0;
        double centerY = row * Board.SIZE + Board.SIZE / 2.0;
        double outerR = MOVE_DOT_OUTER_SIZE / 2.0;
        double innerR = MOVE_DOT_INNER_SIZE / 2.0;

        boolean hovered = mouse.x >= col * Board.SIZE && mouse.x < (col + 1) * Board.SIZE
                       && mouse.y >= row * Board.SIZE && mouse.y < (row + 1) * Board.SIZE;

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
            g2.setColor(new Color(255, 255, 255, 180));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Double(centerX - outerR, centerY - outerR, MOVE_DOT_OUTER_SIZE, MOVE_DOT_OUTER_SIZE));

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g2.fill(new Ellipse2D.Double(centerX - innerR, centerY - innerR, MOVE_DOT_INNER_SIZE, MOVE_DOT_INNER_SIZE));
        }
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
            g2.setFont(new Font("Roboto", Font.BOLD, 18));
            if (gm.currentColor == com.jchess.game.GameManager.WHITE) {
                drawCenteredString(g2, "White's turn", SIDE_PANEL_CENTER_X - 65, WHITE_TURN_Y + 25);
                if (gm.checkingP != null && gm.checkingP.color == com.jchess.game.GameManager.BLACK) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    // Draw a warning message when a king is in check
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X - 60, WHITE_CHECK_Y + 25);
                }
            } else {
                drawCenteredString(g2, "Black's turn", SIDE_PANEL_CENTER_X - 65, BLACK_TURN_Y - 10);
                if (gm.checkingP != null && gm.checkingP.color == com.jchess.game.GameManager.WHITE) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    // Draw a warning message when a king is in check
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X - 60, BLACK_CHECK_Y - 15);
                }
            }
        }
    }

    // Display the game result when the game ends
    private void drawGameResult(Graphics2D g2) {
        if (gm.gameOver || gm.stalemate) {
            int centerY = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT / 2;
            
            if (gm.whiteResign) {
                g2.setFont(new Font("Roboto", Font.BOLD, 32));
                g2.setColor(new Color(126, 255, 140));
                drawCenteredString(g2, "Black Wins", SIDE_PANEL_CENTER_X, centerY - 15);
                g2.setFont(new Font("Roboto", Font.PLAIN, 22));
                g2.setColor(new Color(200, 200, 200));
                drawCenteredString(g2, "by Resignation", SIDE_PANEL_CENTER_X, centerY + 15);
                return;

            } else if (gm.blackResign) {
                g2.setFont(new Font("Roboto", Font.BOLD, 32));
                g2.setColor(new Color(126, 255, 140));
                drawCenteredString(g2, "White Wins", SIDE_PANEL_CENTER_X, centerY - 15);
                g2.setFont(new Font("Roboto", Font.PLAIN, 22));
                g2.setColor(new Color(200, 200, 200));
                drawCenteredString(g2, "by Resignation", SIDE_PANEL_CENTER_X, centerY + 15);
                return;
            } else if (gm.timeOutWinner != null) {
                // Time out win
                g2.setFont(new Font("Roboto", Font.BOLD, 32));
                g2.setColor(new Color(126, 255, 140));
                drawCenteredString(g2, (gm.timeOutWinner == com.jchess.game.GameManager.WHITE ? "White" : "Black") + " Wins", SIDE_PANEL_CENTER_X, centerY - 15);
                g2.setFont(new Font("Roboto", Font.PLAIN, 22));
                g2.setColor(new Color(255, 200, 100));
                drawCenteredString(g2, "by Time", SIDE_PANEL_CENTER_X, centerY + 15);
                return;
            } else {
                String s = (gm.currentColor == com.jchess.game.GameManager.WHITE) ? "White Wins" : "Black Wins";
                g2.setFont(new Font("Roboto", Font.BOLD, 26));
                g2.setColor(new Color(255, 88, 88));
                drawCenteredString(g2, "Checkmate", SIDE_PANEL_CENTER_X, centerY - 20);
                g2.setFont(new Font("Roboto", Font.BOLD, 30));
                g2.setColor(new Color(126, 255, 140));
                drawCenteredString(g2, s, SIDE_PANEL_CENTER_X, centerY + 15);
            }
        }

        if (gm.stalemate) {
            int centerY = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT / 2;
            g2.setFont(new Font("Roboto", Font.BOLD, 32));
            g2.setColor(Color.lightGray);
                drawCenteredString(g2, "Stalemate", SIDE_PANEL_CENTER_X, centerY);
                if (gm.getMoveValidator().isInsufficientMaterial()) {
                g2.setFont(new Font("Roboto", Font.PLAIN, 22));
                g2.setColor(new Color(200, 200, 200));
                drawCenteredString(g2, "by Insufficient Material", SIDE_PANEL_CENTER_X, centerY + 30);
            }
        }
    }

    private void drawResignRed(int x, int y) {
        Graphics2D g2 = (Graphics2D) getGraphics();
        g2.setColor(new Color(255, 80, 80, 180));
        g2.fillOval(x - 15, y - 15, 30, 30);
    }

    private void drawNaviTriangle(Graphics2D g2, int x, int y, String direction) {
        int[] xs = new int[3];
        int[] ys = {y , y - 5, y + 5};
        if (direction.equals("left")){
            xs = new int[]{x - 5, x + 5, x + 5};
        } else if (direction.equals("right")){
            xs = new int[]{x + 5, x - 5, x - 5};
        }

        boolean filled = true;
        Color color = new Color(255, 255, 255, 200);
        g2.setColor(color);
        if (filled) {
            g2.fillPolygon(xs, ys, 3);
        } else {
            g2.drawPolygon(xs, ys, 3);
        }
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

        // Draw timer backgrounds
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillRoundRect(timerX, timerWhiteY, timerWidth, timerHeight, 4, 4);
        g2.fillRoundRect(timerX, timerBlackY, timerWidth, timerHeight, 4, 4);

        // Draw timer text
        g2.setFont(new Font("Roboto", Font.BOLD, 16));
        g2.setColor(Color.white);

        // White's timer (bottom)
        String whiteTime = formatTime(whiteTimeRemaining);
        FontMetrics metrics = g2.getFontMetrics();
        int whiteTextX = timerX + (timerWidth - metrics.stringWidth(whiteTime)) / 2;
        int whiteTextY = timerWhiteY + timerHeight / 2 + metrics.getHeight() / 2 - 3;
        g2.drawString(whiteTime, whiteTextX, whiteTextY);

        // Black's timer (top)
        String blackTime = formatTime(blackTimeRemaining);
        int blackTextX = timerX + (timerWidth - metrics.stringWidth(blackTime)) / 2;
        int blackTextY = timerBlackY + timerHeight / 2 + metrics.getHeight() / 2 - 3;
        g2.drawString(blackTime, blackTextX, blackTextY);

        // Highlight active player's timer
        if (!gm.gameOver && !gm.stalemate) {
            int activeY = (gm.currentColor == com.jchess.game.GameManager.WHITE) ? timerWhiteY : timerBlackY;
            g2.setColor(new Color(255, 255, 255, 60));
            g2.drawRoundRect(timerX - 2, activeY - 2, timerWidth + 4, timerHeight + 4, 6, 6);
        }
    }

    private void drawMoveLog(Graphics2D g2) {
        int boxX = SIDE_PANEL_X + 16;
        int boxY = 150;
        int boxWidth = SIDE_PANEL_WIDTH - 32;
        int boxHeight = 300;
        // Draw the background box for the log
        g2.setColor(new Color(10, 12, 16, 120));
        g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
        g2.setColor(new Color(255, 255, 255, 30));
        g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

        // Header
        g2.setFont(new Font("Roboto", Font.BOLD, 13));
        g2.setColor(new Color(160, 170, 185));
        g2.drawString("MOVE LOG", boxX + 12, boxY + 22);

        // Draw flip button in top right corner
        int buttonWidth = 30;
        int buttonHeight = 20;
        int buttonX = boxX + boxWidth - buttonWidth - 10;
        int buttonY = boxY + 5;
        flipButtonRect.setBounds(buttonX, buttonY, buttonWidth, buttonHeight);

        // Resign buttons for both players
        int resignButtonWidth = 30;
        int resignButtonHeight = 30;
        int resignButtonX = boxX + boxWidth - resignButtonWidth - 10;
        int undoButtonWidth = 30;
        int buttonSpacing = 8;
        int undoButtonX = resignButtonX - undoButtonWidth - buttonSpacing;
        int resignBlackY = boxY + boxHeight - resignButtonHeight - 310;
        int resignWhiteY = boxY + boxHeight - resignButtonHeight + 40;
        resignBlackRect.setBounds(resignButtonX, resignBlackY, resignButtonWidth, resignButtonHeight);
        resignWhiteRect.setBounds(resignButtonX, resignWhiteY, resignButtonWidth, resignButtonHeight);
        undoWhiteRect.setBounds(undoButtonX, resignWhiteY, undoButtonWidth, resignButtonHeight);
        undoBlackRect.setBounds(undoButtonX, resignBlackY, undoButtonWidth, resignButtonHeight);

        // Navigation buttons for move log:
        int navButtonHeight = boxY + 54 - (boxY + 32);
        int navButtonWidth = (boxX + boxWidth - 10 - (boxX + 32)) / 4 + 5;
        int navButtonX1 = boxX + 10;
        int navButtonX2 = boxX + 10 + navButtonWidth;
        int navButtonX3 = boxX + 10 + 2 * navButtonWidth;
        int navButtonX4 = boxX + 10 + 3 * navButtonWidth;
        int navButtonY = boxY + 32;

        // Assign rects in the correct semantic order
        navStartRect.setBounds(navButtonX1, navButtonY, navButtonWidth, navButtonHeight); // |<
        navPrevRect .setBounds(navButtonX2, navButtonY, navButtonWidth, navButtonHeight); // <
        navNextRect .setBounds(navButtonX3, navButtonY, navButtonWidth, navButtonHeight); // >
        navEndRect  .setBounds(navButtonX4, navButtonY, navButtonWidth, navButtonHeight); // >|

        // Check hover states for buttons
        boolean canUndo = gm.canUndo();
        boolean hoverFlip = flipButtonRect.contains(mouse.x, mouse.y);
        boolean hoverUndoWhite = canUndo && undoWhiteRect.contains(mouse.x, mouse.y);
        boolean hoverUndoBlack = canUndo && undoBlackRect.contains(mouse.x, mouse.y);
        boolean hoverResignWhite = resignWhiteRect.contains(mouse.x, mouse.y);
        boolean hoverResignBlack = resignBlackRect.contains(mouse.x, mouse.y);
        boolean hoverNavStart = navStartRect.contains(mouse.x, mouse.y);
        boolean hoverNavPrev  = navPrevRect.contains(mouse.x, mouse.y);
        boolean hoverNavNext  = navNextRect.contains(mouse.x, mouse.y);
        boolean hoverNavEnd   = navEndRect.contains(mouse.x, mouse.y);

        // Draw flip button
        g2.setColor(hoverFlip ? new Color(85, 170, 255, 220) : new Color(40, 115, 220, 180));
        g2.fillRoundRect(buttonX, buttonY, buttonWidth, buttonHeight, 4, 4);
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new java.awt.BasicStroke(1.5f));

        Color navHoverColor = new Color(120, 210, 120, 220);
        Color navBaseColor = new Color(0, 0, 0, 200);

        // Draw undo buttons
        Color undoBase = canUndo ? new Color(128, 128, 128, 200) : new Color(80, 80, 80, 180);
        g2.setColor(hoverUndoWhite ? navHoverColor : undoBase);
        g2.fillRoundRect(undoButtonX, resignWhiteY, undoButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(hoverUndoBlack ? navHoverColor : undoBase);
        g2.fillRoundRect(undoButtonX, resignBlackY, undoButtonWidth, resignButtonHeight, 4, 4);

        // Draw undo icon
        if (undoIcon != null) {
            java.awt.Composite oldComposite = g2.getComposite();
            if (!canUndo) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            }
            g2.drawImage(undoIcon, undoButtonX + (undoButtonWidth - undoIcon.getWidth()) / 2,
                        resignWhiteY + (resignButtonHeight - undoIcon.getHeight()) / 2, null);
            g2.drawImage(undoIcon, undoButtonX + (undoButtonWidth - undoIcon.getWidth()) / 2,
                        resignBlackY + (resignButtonHeight - undoIcon.getHeight()) / 2, null);
            g2.setComposite(oldComposite);
        }

        // Draw resign buttons
        g2.setColor(hoverResignWhite ? new Color(220, 80, 80, 220) : new Color(128, 128, 128, 180));
        g2.fillRoundRect(resignButtonX, resignWhiteY, resignButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(hoverResignBlack ? new Color(220, 80, 80, 220) : new Color(128, 128, 128, 180));
        g2.fillRoundRect(resignButtonX, resignBlackY, resignButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(new Color(200, 200, 200));

        // Draw flip icon
        if (flipBoardIcon != null) {
            g2.drawImage(flipBoardIcon, buttonX + (buttonWidth - flipBoardIcon.getWidth()) / 2, 
                        buttonY + (buttonHeight - flipBoardIcon.getHeight()) / 2, null);

        }

        if (ResignIcon != null) {
            g2.drawImage(ResignIcon, resignButtonX + (resignButtonWidth - ResignIcon.getWidth()) / 2, 
                        resignWhiteY + (resignButtonHeight - ResignIcon.getHeight()) / 2, null);
        }

         if (ResignIcon != null) {
            g2.drawImage(ResignIcon, resignButtonX + (resignButtonWidth - ResignIcon.getWidth()) / 2, 
                        resignBlackY + (resignButtonHeight - ResignIcon.getHeight()) / 2, null);
        }

        // Underline header
        g2.setColor(new Color(255, 255, 255, 20));
        g2.drawLine(boxX + 10, boxY + 32, boxX + boxWidth - 10, boxY + 32);

        // Table headers: No., White, Black
        int col1X = boxX + 15;
        int col2X = boxX + 75;
        int col3X = boxX + 155;
        int rowStartY = boxY + 48;
        int rowHeight = 22;

        // Draw navigation buttons and dividers
        int dividerY1 = boxY + 32;
        int dividerY2 = rowStartY + 6;
        g2.setColor(navStartRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX1, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(navPrevRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX2, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(navNextRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX3, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(navEndRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX4, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.drawLine(navButtonX2, dividerY1, navButtonX2, dividerY2);
        g2.drawLine(navButtonX3, dividerY1, navButtonX3, dividerY2);
        g2.drawLine(navButtonX4, dividerY1, navButtonX4, dividerY2);

        // Draw nav triangles:
        //   slot 1 = double-left  (|<  NavStart)
        //   slot 2 = single-left  (<   NavPrev)
        //   slot 3 = single-right (>   NavNext)
        //   slot 4 = double-right (>|  NavEnd)
        drawNaviTriangle(g2, navButtonX1 + navButtonWidth / 2 - 5, navButtonY + navButtonHeight / 2, "left");
        drawNaviTriangle(g2, navButtonX1 + navButtonWidth / 2 + 5, navButtonY + navButtonHeight / 2, "left");
        drawNaviTriangle(g2, navButtonX2 + navButtonWidth / 2,     navButtonY + navButtonHeight / 2, "left");
        drawNaviTriangle(g2, navButtonX3 + navButtonWidth / 2,     navButtonY + navButtonHeight / 2, "right");
        drawNaviTriangle(g2, navButtonX4 + navButtonWidth / 2 - 5, navButtonY + navButtonHeight / 2, "right");
        drawNaviTriangle(g2, navButtonX4 + navButtonWidth / 2 + 5, navButtonY + navButtonHeight / 2, "right");

        // Divider under table headers
        g2.setColor(new Color(255, 255, 255, 15));
        g2.drawLine(boxX + 10, rowStartY + 6, boxX + boxWidth - 10, rowStartY + 6);

        // Table headers: No., White, Black
        int startY = rowStartY + 22;
        int totalMoves = gm.moves.size();
        int totalPairs = (totalMoves + 1) / 2;
        int maxVisible = 10;
        
        // Move log entries
        g2.setFont(new Font("Roboto", Font.PLAIN, 13));
        // activeMoveIndex is the index of the move that should be highlighted as the "current" move in the log
        int activeMoveIndex = (gm.getViewMoveIndex() == -1) ? (totalMoves - 1) : (gm.getViewMoveIndex() - 1);
        for (int i = 0; i < maxVisible; i++) {
            int pairIndex = gm.scrollStartLine + i;
            if (pairIndex >= totalPairs) {
                break;
            }

            int currentY = startY + i * rowHeight;

            // Draw move number
            g2.setColor(new Color(110, 120, 135));
            g2.drawString((pairIndex + 1) + ".", col1X, currentY);

            // Draw White's move
            int whiteMoveIndex = pairIndex * 2;
            if (whiteMoveIndex < totalMoves) {
                com.jchess.util.MoveRecord whiteMove = gm.moves.get(whiteMoveIndex);
                boolean isLastMove = (whiteMoveIndex == activeMoveIndex);

                if (isLastMove) {
                    // Highlight last move in the log
                    g2.setColor(new Color(0, 120, 215, 60)); // soft blue highlight
                    g2.fillRoundRect(col2X - 5, currentY - 14, 65, 18, 4, 4);
                    g2.setColor(new Color(255, 255, 255));
                } else {
                    g2.setColor(new Color(210, 215, 225));
                }
                g2.drawString(whiteMove.san, col2X, currentY);
            }

            // Draw Black's move
            int blackMoveIndex = pairIndex * 2 + 1;
            if (blackMoveIndex < totalMoves) {
                com.jchess.util.MoveRecord blackMove = gm.moves.get(blackMoveIndex);
                boolean isLastMove = (blackMoveIndex == activeMoveIndex);

                if (isLastMove) {
                    // Highlight last move in the log
                    g2.setColor(new Color(0, 120, 215, 60)); // soft blue highlight
                    g2.fillRoundRect(col3X - 5, currentY - 14, 65, 18, 4, 4);
                    g2.setColor(new Color(255, 255, 255));
                } else {
                    g2.setColor(new Color(210, 215, 225));
                }
                g2.drawString(blackMove.san, col3X, currentY);
            }
        }

        // Draw scrollbar if scrollable
        if (totalPairs > maxVisible) {
            int scrollbarX = boxX + boxWidth - 8;
            int scrollbarY = startY - 12;
            int scrollbarHeight = boxHeight - (scrollbarY - boxY) - 10;
            int scrollbarWidth = 4;

            // Track
            g2.setColor(new Color(255, 255, 255, 10));
            g2.fillRoundRect(scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight, 2, 2);

            // Thumb
            int thumbHeight = scrollbarHeight * maxVisible / totalPairs;
            if (thumbHeight < 15)
                thumbHeight = 15;
            int thumbY = scrollbarY + (scrollbarHeight - thumbHeight) * gm.scrollStartLine / (totalPairs - maxVisible);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(scrollbarX, thumbY, scrollbarWidth, thumbHeight, 2, 2);
        }
    }

    // Draw text centered at a specified position
    private void drawCenteredString(Graphics2D g2, String text, int centerX, int baselineY) {
        FontMetrics metrics = g2.getFontMetrics();
        int textX = centerX - metrics.stringWidth(text) / 2;
        g2.drawString(text, textX, baselineY);
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

