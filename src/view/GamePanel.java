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
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.InputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;

import controller.Mouse;
import model.Board;
import piece.Bishop;
import piece.King;
import piece.Knight;
import piece.Pawn;
import piece.Piece;
import piece.Queen;
import piece.Rook;

public class GamePanel extends JPanel {
    public static final int WIDTH = 900;
    public static final int HEIGHT = 600;
    private static final int FPS = 60;
    private static final int TIMER_DELAY = 1000 / FPS;
    private static final int BOARD_PIXEL_SIZE = Board.SIZE * 8;
    private static final int SIDE_PANEL_X = BOARD_PIXEL_SIZE + 12;
    private static final int SIDE_PANEL_Y = 24;
    private static final int SIDE_PANEL_WIDTH = WIDTH - SIDE_PANEL_X - 16;
    private static final int SIDE_PANEL_HEIGHT = HEIGHT - SIDE_PANEL_Y * 2;
    private static final int PROMOTION_COL = 9;
    private static final int STATUS_X = SIDE_PANEL_X + 24;
    private static final int BLACK_TURN_Y = SIDE_PANEL_Y + 96;
    private static final int BLACK_CHECK_Y = BLACK_TURN_Y + 36;
    private static final int WHITE_TURN_Y = SIDE_PANEL_Y + SIDE_PANEL_HEIGHT - 86;
    private static final int WHITE_CHECK_Y = WHITE_TURN_Y + 36;
    private static final int PROMOTION_LABEL_Y = SIDE_PANEL_Y + 126;
    private static final int MOVE_DOT_OUTER_SIZE = 30;
    private static final int MOVE_DOT_INNER_SIZE = 15;
    private static final float BACKGROUND_ALPHA = 0.70f;

    private Timer gameTimer;
    private final Board board;
    private final Mouse mouse;
    private BufferedImage background;

    public static ArrayList<Piece> pieces;
    public static ArrayList<Piece> simPieces;
    private final ArrayList<Piece> promoPieces;
    private final ArrayList<Point> legalMoveSquares;
    public static Piece castlingP;
    private Piece activeP, checkingP;

    public static final int WHITE = 0;
    public static final int BLACK = 1;
    private int currentColor;

    private boolean canMove;
    private boolean validSquare;
    private boolean promotion;
    private boolean gameOver;
    private boolean stalemate;
    private boolean mousePressedLastFrame = false;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.black);
        setDoubleBuffered(true);
        
        mouse = new Mouse();
        board = new Board();
        background = loadBackground();
        pieces = new ArrayList<>();
        simPieces = new ArrayList<>();
        promoPieces = new ArrayList<>();
        legalMoveSquares = new ArrayList<>();
        
        addMouseMotionListener(mouse);
        addMouseListener(mouse);

        setPieces();
        copyPieces(pieces, simPieces);
    }

    public void startGame() {
        if (gameTimer != null && gameTimer.isRunning()) {
            return;
        }

        gameTimer = new Timer(TIMER_DELAY, e -> {
            update();
            repaint();
        });
        gameTimer.setCoalesce(true);
        gameTimer.start();
    }

    public void setPieces() {
        pieces.add(new Pawn(0, 6, WHITE));
        pieces.add(new Pawn(1, 6, WHITE));
        pieces.add(new Pawn(2, 6, WHITE));
        pieces.add(new Pawn(3, 6, WHITE));
        pieces.add(new Pawn(4, 6, WHITE));
        pieces.add(new Pawn(5, 6, WHITE));
        pieces.add(new Pawn(6, 6, WHITE));
        pieces.add(new Pawn(7, 6, WHITE));
        pieces.add(new Rook(0, 7, WHITE));
        pieces.add(new Rook(7, 7, WHITE));
        pieces.add(new Knight(1, 7, WHITE));
        pieces.add(new Knight(6, 7, WHITE));
        pieces.add(new Bishop(2, 7, WHITE));
        pieces.add(new Bishop(5, 7, WHITE));
        pieces.add(new Queen(3, 7, WHITE));
        pieces.add(new King(4, 7, WHITE));

        pieces.add(new Pawn(0, 1, BLACK));
        pieces.add(new Pawn(1, 1, BLACK));
        pieces.add(new Pawn(2, 1, BLACK));
        pieces.add(new Pawn(3, 1, BLACK));
        pieces.add(new Pawn(4, 1, BLACK));
        pieces.add(new Pawn(5, 1, BLACK));
        pieces.add(new Pawn(6, 1, BLACK));
        pieces.add(new Pawn(7, 1, BLACK));
        pieces.add(new Rook(0, 0, BLACK));
        pieces.add(new Rook(7, 0, BLACK));
        pieces.add(new Knight(1, 0, BLACK));
        pieces.add(new Knight(6, 0, BLACK));
        pieces.add(new Bishop(2, 0, BLACK));
        pieces.add(new Bishop(5, 0, BLACK));
        pieces.add(new Queen(3, 0, BLACK));
        pieces.add(new King(4, 0, BLACK));
    }

    private void copyPieces(ArrayList<Piece> src, ArrayList<Piece> tgt) {
        tgt.clear();
        tgt.addAll(src);
    }

    private BufferedImage loadBackground() {
        try (InputStream in = GamePanel.class.getResourceAsStream("/resources/background.jpeg")) {
            if (in == null) {
                System.err.println("Failed to load image: /resources/background.jpeg");
                return null;
            }

            BufferedImage original = ImageIO.read(in);
            BufferedImage fitted = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = fitted.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawImageCover(g2, original, 0, 0, WIDTH, HEIGHT);
            g2.dispose();

            return blurImage(fitted);
        } catch (Exception e) {
            System.err.println("Failed to load image: /resources/background.jpeg");
            return null;
        }
    }

    private void drawImageCover(Graphics2D g2, BufferedImage img, int x, int y, int width, int height) {
        double scale = Math.max((double) width / img.getWidth(), (double) height / img.getHeight());
        int drawWidth = (int) Math.round(img.getWidth() * scale);
        int drawHeight = (int) Math.round(img.getHeight() * scale);
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        g2.drawImage(img, drawX, drawY, drawWidth, drawHeight, null);
    }

    private BufferedImage blurImage(BufferedImage source) {
        int size = 9;
        float weight = 1f / (size * size);
        float[] data = new float[size * size];

        for (int i = 0; i < data.length; i++) {
            data[i] = weight;
        }

        Kernel kernel = new Kernel(size, size, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        op.filter(source, blurred);
        return blurred;
    }

    private void update() {
        if (promotion) {
            promote();
            return;
        }

        if (gameOver || stalemate) {
            return;
        }
        
        boolean mouseJustPressed = mouse.pressed && !mousePressedLastFrame;
        boolean mouseJustReleased = !mouse.pressed && mousePressedLastFrame;
        mousePressedLastFrame = mouse.pressed;
        
        if (activeP == null) {
            if (mouseJustPressed) {
                selectActivePiece();
            }
        } else {
            if (mouse.pressed) {
                if (mouseJustPressed) {
                    int mouseCol = mouse.x / Board.SIZE;
                    int mouseRow = mouse.y / Board.SIZE;
                    Piece clickedPiece = getPieceAt(mouseCol, mouseRow);

                    if (clickedPiece == activeP) {
                        cancelMove();
                    } else if (clickedPiece != null && clickedPiece.color == currentColor) {
                        activeP.resetPosition();
                        activeP = clickedPiece;
                        canMove = false;
                        validSquare = false;
                        refreshLegalMoveSquares();
                    } else {
                        simulate();
                    }
                } else {
                    simulate();
                }
            } else if (mouseJustReleased) {
                int currentCol = mouse.x / Board.SIZE;
                int currentRow = mouse.y / Board.SIZE;

                if (currentCol == activeP.preCol && currentRow == activeP.preRow) {
                    activeP.resetPosition();
                    canMove = false;
                    validSquare = false;
                } else {
                    if (validSquare) {
                        commitMove();
                    } else {
                        cancelMove();
                    }
                }
            }
        }
    }

//    private void handleMousePressed() {
//        if (activeP == null) {
//            selectActivePiece();
//            return;
//        }
//
//        simulate();
//    }

    private void selectActivePiece() {
        int mouseCol = mouse.x / Board.SIZE;
        int mouseRow = mouse.y / Board.SIZE;

        if (!isWithinBoard(mouseCol, mouseRow)) {
            return;
        }

        for (Piece p : simPieces) {
            if (p.color == currentColor && p.col == mouseCol && p.row == mouseRow) {
                activeP = p;
                canMove = false;
                validSquare = false;
                refreshLegalMoveSquares();
                return;
            }
        }
    }

//    private void handleMouseReleased() {
//        if (activeP == null) {
//            return;
//        }
//
//        if (validSquare) {
//            commitMove();
//        } else {
//            cancelMove();
//        }
//    }

    private void commitMove() {
        copyPieces(simPieces, pieces);
        activeP.updatePosition();
        legalMoveSquares.clear();

        if (castlingP != null) {
            castlingP.updatePosition();
        }

        if (canPromote()) {
            promotion = true;
        } else {
            finishTurn();
        }
    }

    private void cancelMove() {
        copyPieces(pieces, simPieces);
        activeP.resetPosition();
        activeP = null;
        castlingP = null;
        canMove = false;
        validSquare = false;
        legalMoveSquares.clear();
    }

    private void promote() {
        if (mouse.pressed) {
            for (Piece p : promoPieces) {
                if (p.col == mouse.x / Board.SIZE && p.row == mouse.y / Board.SIZE) {
                    switch (p.type) {
                        case "ROOK":
                            simPieces.add(new Rook(activeP.col, activeP.row, currentColor));
                            break;
                        case "KNIGHT":
                            simPieces.add(new Knight(activeP.col, activeP.row, currentColor));
                            break;
                        case "BISHOP":
                            simPieces.add(new Bishop(activeP.col, activeP.row, currentColor));
                            break;
                        case "QUEEN":
                            simPieces.add(new Queen(activeP.col, activeP.row, currentColor));
                            break;
                    }
                    simPieces.remove(activeP.getIndex());
                    copyPieces(simPieces, pieces);
                    activeP = null;
                    promotion = false;
                    canMove = false;
                    validSquare = false;
                    legalMoveSquares.clear();
                    finishTurn();
                    return;
                }
            }
        }
    }

    private void simulate() {
        canMove = false;
        validSquare = false;

        copyPieces(pieces, simPieces);

        if (castlingP != null) {
            castlingP.col = castlingP.preCol;
            castlingP.x = castlingP.getX(castlingP.col);
            castlingP = null;
        }

        activeP.x = mouse.x - Board.SIZE / 2;
        activeP.y = mouse.y - Board.SIZE / 2;
        activeP.col = activeP.getCol(activeP.x);
        activeP.row = activeP.getRow(activeP.y);

        if (activeP.canMove(activeP.col, activeP.row)) {
            canMove = true;

            if (activeP.hittingP != null) {
                int hitIndex = activeP.hittingP.getIndex();
                if (hitIndex >= 0) {
                    simPieces.remove(hitIndex);
                }
            }

            boolean castlingThroughCheck = isCastlingThroughCheck(activeP);
            checkCastling();

            if (!castlingThroughCheck && !isKingInCheck(currentColor)) {
                validSquare = true;
            }
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        
        drawBackground(g2);
        board.draw(g2);
        drawSidePanel(g2);

        drawCheckedKingGlow(g2);
        drawPieces(g2);
        drawLegalMoveDots(g2);
        drawActivePiece(g2);
        drawStatus(g2);
        drawGameResult(g2);
        
    }

    private void drawPieces(Graphics2D g2) {
        for (Piece p : new ArrayList<>(simPieces)) {
            p.draw(g2);
        }
    }

    private void drawActivePiece(Graphics2D g2) {
        if (activeP == null) {
            return;
        }

        if (canMove && validSquare) {
            drawMoveDot(g2, activeP.col, activeP.row);
        }

        activeP.draw(g2);
    }
    
    // Mark squares that are legal moves for the piece
    private void drawLegalMoveDots(Graphics2D g2) {
        if (activeP == null || legalMoveSquares.isEmpty() || gameOver || stalemate) {
            return;
        }

        for (Point square : legalMoveSquares) {
            drawMoveDot(g2, square.x, square.y);
        }
    }
    
    // Draw the dot for the highlight
    private void drawMoveDot(Graphics2D g2, int col, int row) {
        double centerX = col * Board.SIZE + Board.SIZE / 2.0;
        double centerY = row * Board.SIZE + Board.SIZE / 2.0;
        Ellipse2D.Double outerCircle = createCenteredCircle(centerX, centerY, MOVE_DOT_OUTER_SIZE);
        Ellipse2D.Double innerCircle = createCenteredCircle(centerX, centerY, MOVE_DOT_INNER_SIZE);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(outerCircle);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g2.fill(innerCircle);

    }

    private Ellipse2D.Double createCenteredCircle(double centerX, double centerY, double diameter) {
        double radius = diameter / 2.0;
        return new Ellipse2D.Double(centerX - radius, centerY - radius, diameter, diameter);
    }

    private void drawCheckedKingGlow(Graphics2D g2) {
        Piece king = getCheckedKing();
        if (king == null) return;

        double centerX = king.col * Board.SIZE + Board.SIZE / 2.0;
        double centerY = king.row * Board.SIZE + Board.SIZE / 2.0;

        Composite oldComposite = g2.getComposite();

        for (int layer = 6; layer >= 1; layer--) {
            int diameter = Board.SIZE + layer * 10;
            float alpha = (0.2f / 6) * layer;  
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(new Color(255, 80, 80));
            g2.fill(createCenteredCircle(centerX, centerY, diameter));
        }

        g2.setComposite(oldComposite);
    }


    private Piece getCheckedKing() {
        if (checkingP == null) {
            return null;
        }

        return getKing(getOppositeColor(checkingP.color));
    }

    private void drawStatus(Graphics2D g2) {
        if (gameOver || stalemate) {
            return;
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 25));
        g2.setColor(Color.white);


        if (promotion) {
            g2.drawString("Promote to:", STATUS_X, PROMOTION_LABEL_Y);
            for (Piece p : promoPieces) {
                g2.drawImage(p.img, p.getX(p.col), p.getY(p.row),
                        Board.SIZE, Board.SIZE, null);
            }
        } else {
            if (currentColor == WHITE) {
                g2.drawString("White's turn", STATUS_X, WHITE_TURN_Y);
                if (checkingP != null && checkingP.color == BLACK) {
                    g2.setColor(Color.red);
                    g2.drawString("King in check!", STATUS_X, WHITE_CHECK_Y);
                }
            } else {
                g2.drawString("Black's turn", STATUS_X, BLACK_TURN_Y);
                if (checkingP != null && checkingP.color == WHITE) {
                    g2.setColor(Color.red);
                    g2.drawString("King in check!", STATUS_X, BLACK_CHECK_Y);
                }
            }
        }
    }
    
    
    
    private void drawGameResult(Graphics2D g2) {
        if (gameOver) {
            String s = (currentColor == WHITE) ? "White Wins" : "Black Wins";
            g2.setFont(new Font("Arial", Font.BOLD, 28));
            g2.setColor(new Color(255, 88, 88));
            drawCenteredString(g2, "Checkmate", SIDE_PANEL_X, SIDE_PANEL_WIDTH, 246);
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            g2.setColor(new Color(126, 255, 140));
            drawCenteredString(g2, s, SIDE_PANEL_X, SIDE_PANEL_WIDTH, 292);
        }

        if (stalemate) {
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            g2.setColor(Color.lightGray);
            drawCenteredString(g2, "Stalemate", SIDE_PANEL_X, SIDE_PANEL_WIDTH, 292);
        }
    }

    private void drawCenteredString(Graphics2D g2, String text, int x, int width, int baselineY) {
        FontMetrics metrics = g2.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        g2.drawString(text, textX, baselineY);
    }

    private void drawBackground(Graphics2D g2) {
        if (background == null) {
            g2.setColor(new Color(20, 21, 24));
            g2.fillRect(0, 0, getWidth(), getHeight());
            return;
        }

        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKGROUND_ALPHA));
        g2.drawImage(background, 0, 0, null);
        g2.setComposite(oldComposite);
    }

    private void drawSidePanel(Graphics2D g2) {
        g2.setColor(new Color(12, 14, 18, 176));
        g2.fillRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);
        g2.setColor(new Color(255, 255, 255, 48));
        g2.drawRoundRect(SIDE_PANEL_X, SIDE_PANEL_Y, SIDE_PANEL_WIDTH, SIDE_PANEL_HEIGHT, 8, 8);
    }

    private void changePlayer() {
        if (currentColor == WHITE) {
            currentColor = BLACK;
            for (Piece p : pieces) {
                if (p.color == BLACK) {
                    p.twoStepped = false;
                }
            }
        } else {
            currentColor = WHITE;
            for (Piece p : pieces) {
                if (p.color == WHITE) {
                    p.twoStepped = false;
                }
            }
        }
        activeP = null;
    }

    private void finishTurn() {
        int opponent = getOppositeColor(currentColor);
        checkingP = findCheckingPiece(opponent);
        boolean opponentHasLegalMove = hasLegalMove(opponent);

        if (checkingP != null && !opponentHasLegalMove) {
            gameOver = true;
            activeP = null;
            legalMoveSquares.clear();
        } else if (checkingP == null && !opponentHasLegalMove) {
            stalemate = true;
            activeP = null;
            legalMoveSquares.clear();
        } else {
            changePlayer();
        }
    }

    private void checkCastling() {
        if (castlingP != null) {
            if (castlingP.col == 0) {
                castlingP.col += 3;
            } else if (castlingP.col == 7) {
                castlingP.col -= 2;
            }
            castlingP.x = castlingP.getX(castlingP.col);
        }
    }

    private boolean canPromote() {
        if ("PAWN".equals(activeP.type)) {
            if ((currentColor == WHITE && activeP.row == 0) || (currentColor == BLACK && activeP.row == 7)) {
                promoPieces.clear();
                promoPieces.add(new Rook(PROMOTION_COL, 2, currentColor));
                promoPieces.add(new Knight(PROMOTION_COL, 3, currentColor));
                promoPieces.add(new Bishop(PROMOTION_COL, 4, currentColor));
                promoPieces.add(new Queen(PROMOTION_COL, 5, currentColor));
                return true;
            }
        }
        return false;
    }

    private boolean isWithinBoard(int col, int row) {
        return col >= 0 && col < 8 && row >= 0 && row < 8;
    }

    private Piece getKing(int color) {
        for (Piece p : simPieces) {
            if ("KING".equals(p.type) && p.color == color) {
                return p;
            }
        }
        return null;
    }

    private Piece findCheckingPiece(int kingColor) {
        Piece king = getKing(kingColor);
        if (king == null) return null;

        for (Piece p : simPieces) {
            if (p.color != kingColor && attacksSquare(p, king.col, king.row)) {
                return p;
            }
        }

        return null;
    }

    private boolean isKingInCheck(int kingColor) {
        return findCheckingPiece(kingColor) != null;
    }

    private boolean isSquareAttacked(int col, int row, int attackingColor) {
        for (Piece p : simPieces) {
            if (p.color == attackingColor && attacksSquare(p, col, row)) {
                return true;
            }
        }

        return false;
    }

    private boolean attacksSquare(Piece piece, int targetCol, int targetRow) {
        int colDiff = Math.abs(targetCol - piece.col);
        int rowDiff = Math.abs(targetRow - piece.row);

        switch (piece.type) {
            case "PAWN":
                int direction = piece.color == WHITE ? -1 : 1;
                return rowDiff == 1
                        && targetRow == piece.row + direction
                        && colDiff == 1;
            case "KNIGHT":
                return (colDiff == 1 && rowDiff == 2) || (colDiff == 2 && rowDiff == 1);
            case "BISHOP":
                return colDiff == rowDiff && isPathClear(piece, targetCol, targetRow);
            case "ROOK":
                return (targetCol == piece.col || targetRow == piece.row)
                        && isPathClear(piece, targetCol, targetRow);
            case "QUEEN":
                boolean straight = targetCol == piece.col || targetRow == piece.row;
                boolean diagonal = colDiff == rowDiff;
                return (straight || diagonal) && isPathClear(piece, targetCol, targetRow);
            case "KING":
                return colDiff <= 1 && rowDiff <= 1;
            default:
                return false;
        }
    }

    private boolean isPathClear(Piece piece, int targetCol, int targetRow) {
        int colDirection = Integer.compare(targetCol, piece.col);
        int rowDirection = Integer.compare(targetRow, piece.row);
        int currentCol = piece.col + colDirection;
        int currentRow = piece.row + rowDirection;

        while (currentCol != targetCol || currentRow != targetRow) {
            if (getPieceAt(currentCol, currentRow) != null) {
                return false;
            }
            currentCol += colDirection;
            currentRow += rowDirection;
        }

        return true;
    }

    private Piece getPieceAt(int col, int row) {
        for (Piece p : simPieces) {
            if (p.col == col && p.row == row) {
                return p;
            }
        }
        return null;
    }

    private boolean isCastlingThroughCheck(Piece king) {
        if (king == null || !"KING".equals(king.type) || Math.abs(king.col - king.preCol) != 2) {
            return false;
        }

        int originalCol = king.col;
        int originalRow = king.row;
        int direction = king.col > king.preCol ? 1 : -1;
        int attackingColor = getOppositeColor(king.color);

        for (int step = 0; step <= 2; step++) {
            int col = king.preCol + direction * step;
            king.col = col;
            king.row = king.preRow;

            if (isSquareAttacked(col, king.preRow, attackingColor)) {
                king.col = originalCol;
                king.row = originalRow;
                return true;
            }
        }

        king.col = originalCol;
        king.row = originalRow;
        return false;
    }

    private boolean hasLegalMove(int color) {
        Piece savedCastlingP = castlingP;

        for (Piece piece : new ArrayList<>(pieces)) {
            if (piece.color != color) {
                continue;
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (canLegallyMove(piece, col, row)) {
                        castlingP = savedCastlingP;
                        copyPieces(pieces, simPieces);
                        return true;
                    }
                }
            }
        }

        castlingP = savedCastlingP;
        copyPieces(pieces, simPieces);
        return false;
    }

    private boolean canLegallyMove(Piece piece, int targetCol, int targetRow) {
        int originalCol = piece.col;
        int originalRow = piece.row;
        int originalX = piece.x;
        int originalY = piece.y;
        Piece originalHittingP = piece.hittingP;
        Piece savedCastlingP = castlingP;

        copyPieces(pieces, simPieces);
        castlingP = null;

        piece.col = targetCol;
        piece.row = targetRow;

        boolean legal = false;

        if (piece.canMove(targetCol, targetRow)) {
            if (piece.hittingP != null) {
                int hitIndex = piece.hittingP.getIndex();
                if (hitIndex >= 0) {
                    simPieces.remove(hitIndex);
                }
            }

            boolean castlingThroughCheck = isCastlingThroughCheck(piece);
            Piece rook = castlingP;
            int rookCol = 0;
            int rookX = 0;

            if (rook != null) {
                rookCol = rook.col;
                rookX = rook.x;
            }

            checkCastling();
            legal = !castlingThroughCheck && !isKingInCheck(piece.color);

            if (rook != null) {
                rook.col = rookCol;
                rook.x = rookX;
            }
        }

        piece.col = originalCol;
        piece.row = originalRow;
        piece.x = originalX;
        piece.y = originalY;
        piece.hittingP = originalHittingP;
        castlingP = savedCastlingP;
        copyPieces(pieces, simPieces);

        return legal;
    }

    private void refreshLegalMoveSquares() {
        legalMoveSquares.clear();

        if (activeP == null) {
            return;
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (canLegallyMove(activeP, col, row)) {
                    legalMoveSquares.add(new Point(col, row));
                }
            }
        }

        copyPieces(pieces, simPieces);
    }

    private int getOppositeColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }
    
}
