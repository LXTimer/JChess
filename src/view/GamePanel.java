package view;

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
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;

import controller.Mouse;
import model.Board;
import piece.Piece;

public class GamePanel extends JPanel {

    // Declare all the variables
    public static final int WIDTH  = 900;
    public static final int HEIGHT = 600;
    private static final int FPS = 120;
    private static final int TIMER_DELAY = 1000 / FPS;
    private static final int BOARD_PIXEL_SIZE    = Board.SIZE * 8;
    private static final int SIDE_PANEL_X        = BOARD_PIXEL_SIZE + 12;
    private static final int SIDE_PANEL_Y        = 24;
    private static final int SIDE_PANEL_WIDTH    = WIDTH - SIDE_PANEL_X - 16;
    private static final int SIDE_PANEL_HEIGHT   = HEIGHT - SIDE_PANEL_Y * 2;
    private static final int SIDE_PANEL_CENTER_X = SIDE_PANEL_X + SIDE_PANEL_WIDTH / 2;
    private static final int BLACK_TURN_Y        = SIDE_PANEL_Y + 96;
    private static final int BLACK_CHECK_Y       = BLACK_TURN_Y + 36;
    private static final int WHITE_TURN_Y        = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT - 86;
    private static final int WHITE_CHECK_Y       = WHITE_TURN_Y + 36;
    private static final int PROMOTION_LABEL_Y   = SIDE_PANEL_Y + 126;
    private static final int MOVE_DOT_OUTER_SIZE = 30;
    private static final int MOVE_DOT_INNER_SIZE = 15;
    private static final float BACKGROUND_ALPHA  = 0.70f;

    private Timer gameTimer;
    private final Board board;
    private final Mouse mouse;
    private final GameManager gm;
    private BufferedImage background;
    private boolean mousePressedLastFrame = false;

    // Constructor
    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.black);
        setDoubleBuffered(true);

        mouse = new Mouse();
        board = new Board();
        gm    = new GameManager(mouse);
        background = loadBackground();

        addMouseMotionListener(mouse);
        addMouseListener(mouse);
    }

    // Start method
    public void startGame() {
        if (gameTimer != null && gameTimer.isRunning()) {
            return;
        }

        gameTimer = new Timer(TIMER_DELAY, e -> {
            boolean mouseJustPressed  = mouse.pressed && !mousePressedLastFrame;
            boolean mouseJustReleased = !mouse.pressed && mousePressedLastFrame;
            mousePressedLastFrame = mouse.pressed;
            // Main game loop update method
            gm.update(mouseJustPressed, mouseJustReleased);
            repaint();
        });
        gameTimer.setCoalesce(true);
        gameTimer.start();
    }

    // Helper method for loading background images
    private BufferedImage loadBackground() {
        try (InputStream in = GamePanel.class.getResourceAsStream("/resources/background.jpeg")) {
            if (in == null) {
                System.err.println("Failed to load image: /resources/background.jpeg");
                return null;
            }

            BufferedImage original = ImageIO.read(in);
            BufferedImage fitted   = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = fitted.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Draw and scale the background image to fill the panel
            double scale   = Math.max((double) WIDTH / original.getWidth(), (double) HEIGHT / original.getHeight());
            int drawWidth  = (int) Math.round(original.getWidth()  * scale);
            int drawHeight = (int) Math.round(original.getHeight() * scale);
            int drawX      = (WIDTH  - drawWidth)  / 2;
            int drawY      = (HEIGHT - drawHeight) / 2;
            g2.drawImage(original, drawX, drawY, drawWidth, drawHeight, null);
            g2.dispose();

            // Apply a blur effect to the background image
            int size     = 15;
            float weight = 1f / (size * size);
            float[] data = new float[size * size];
            Arrays.fill(data, weight);
            return new ConvolveOp(new Kernel(size, size, data), ConvolveOp.EDGE_ZERO_FILL, null).filter(fitted, null);
        } catch (Exception e) {
            System.err.println("Failed to load image: /resources/background.jpeg");
            return null;
        }
    }

    @Override
    // Main rendering method for the game panel
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Draw the game background
        if (background == null) {
            g2.setColor(new Color(20, 21, 24));
            g2.fillRect(0, 0, getWidth(), getHeight());
        } else {
            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKGROUND_ALPHA));
            g2.drawImage(background, 0, 0, null);
            g2.setComposite(oldComposite);
        }

        board.draw(g2);

        // Draw the side information panel
        g2.setColor(new Color(12, 14, 18, 176));
        g2.fillRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);
        g2.setColor(new Color(255, 255, 255, 48));
        g2.drawRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);

        // Highlight the king when it is in check
        drawCheckedKingGlow(g2);

        // Draw all pieces currently on the board
        for (Piece p : new ArrayList<>(gm.simPieces)) {
            p.draw(g2);
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
            gm.activeP.draw(g2);
        }

        // Draw turn information and promotion options
        drawStatus(g2);
        // Display the game result when the game ends
        drawGameResult(g2);
    }

    // Draw the dot for the highlight
    private void drawMoveDot(Graphics2D g2, int col, int row) {
        double centerX = col * Board.SIZE + Board.SIZE / 2.0;
        double centerY = row * Board.SIZE + Board.SIZE / 2.0;
        double outerR  = MOVE_DOT_OUTER_SIZE / 2.0;
        double innerR  = MOVE_DOT_INNER_SIZE / 2.0;

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new Ellipse2D.Double(centerX - outerR, centerY - outerR, MOVE_DOT_OUTER_SIZE, MOVE_DOT_OUTER_SIZE));

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g2.fill(new Ellipse2D.Double(centerX - innerR, centerY - innerR, MOVE_DOT_INNER_SIZE, MOVE_DOT_INNER_SIZE));
    }

    // Highlight the king when it is in check
    private void drawCheckedKingGlow(Graphics2D g2) {
        if (gm.checkingP == null) return;
        Piece king = null;
        for (Piece p : gm.simPieces) {
            if ("KING".equals(p.type) && p.color == gm.getOppositeColor(gm.checkingP.color)) {
                king = p;
                break;
            }
        }
        if (king == null) return;

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
            g2.setFont(new Font("Roboto", Font.BOLD, 24));
            if (gm.currentColor == GameManager.WHITE) {
                drawCenteredString(g2, "White's turn", SIDE_PANEL_CENTER_X, WHITE_TURN_Y);
                if (gm.checkingP != null && gm.checkingP.color == GameManager.BLACK) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    // Draw a warning message when a king is in check
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X, WHITE_CHECK_Y);
                }
            } else {
                drawCenteredString(g2, "Black's turn", SIDE_PANEL_CENTER_X, BLACK_TURN_Y);
                if (gm.checkingP != null && gm.checkingP.color == GameManager.WHITE) {
                    g2.setFont(new Font("Roboto", Font.BOLD, 20));
                    g2.setColor(Color.red);
                    // Draw a warning message when a king is in check
                    drawCenteredString(g2, "King in check!", SIDE_PANEL_CENTER_X, BLACK_CHECK_Y);
                }
            }
        }
    }

    // Display the game result when the game ends
    private void drawGameResult(Graphics2D g2) {
        if (gm.gameOver) {
            String s = (gm.currentColor == GameManager.WHITE) ? "White Wins" : "Black Wins";
            g2.setFont(new Font("Roboto", Font.BOLD, 28));
            g2.setColor(new Color(255, 88, 88));
            drawCenteredString(g2, "Checkmate", SIDE_PANEL_CENTER_X, 246);
            g2.setFont(new Font("Roboto", Font.BOLD, 34));
            g2.setColor(new Color(126, 255, 140));
            drawCenteredString(g2, s, SIDE_PANEL_CENTER_X, 292);
        }

        if (gm.stalemate) {
            g2.setFont(new Font("Roboto", Font.BOLD, 34));
            g2.setColor(Color.lightGray);
            drawCenteredString(g2, "Stalemate", SIDE_PANEL_CENTER_X, 292);
        }
    }

    // Draw text centered at a specified position
    private void drawCenteredString(Graphics2D g2, String text, int centerX, int baselineY) {
        FontMetrics metrics = g2.getFontMetrics();
        int textX = centerX - metrics.stringWidth(text) / 2;
        g2.drawString(text, textX, baselineY);
    }
}