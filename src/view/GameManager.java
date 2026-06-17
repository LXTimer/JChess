package view;

import controller.Mouse;
import java.awt.Point;
import java.util.ArrayList;

import model.Board;
import piece.Bishop;
import piece.King;
import piece.Knight;
import piece.Pawn;
import piece.Piece;
import piece.Queen;
import piece.Rook;

public class GameManager {

    public static final int WHITE = 0;
    public static final int BLACK = 1;

    // Declare all the variables
    public ArrayList<Piece> pieces = new ArrayList<>();
    public static ArrayList<Piece> simPieces = new ArrayList<>();
    public ArrayList<Piece> promoPieces = new ArrayList<>();
    public ArrayList<Point> legalMoveSquares = new ArrayList<>();

    public static Piece castlingP;
    public Piece activeP;
    public Piece checkingP;

    public int currentColor;

    public boolean canMove;
    public boolean validSquare;
    public boolean promotion;
    public boolean gameOver;
    public boolean stalemate;

    private final Mouse mouse;

    // Constructor
    public GameManager(Mouse mouse) {
        this.mouse = mouse;
        setPieces();
        copyPieces(pieces, simPieces);
    }

    // Method that sets up all the pieces in the beginning
    public void setPieces() {
        for (int col = 0; col < 8; col++) {
            pieces.add(new Pawn(col, 6, WHITE));
            pieces.add(new Pawn(col, 1, BLACK));
        }

        int[] backRankCols = {0, 7, 1, 6, 2, 5, 3, 4};
        for (int i = 0; i < backRankCols.length; i++) {
            int col = backRankCols[i];
            Piece whitePiece, blackPiece;
            if      (i < 2) { whitePiece = new Rook(col, 7, WHITE);   blackPiece = new Rook(col, 0, BLACK); }
            else if (i < 4) { whitePiece = new Knight(col, 7, WHITE); blackPiece = new Knight(col, 0, BLACK); }
            else if (i < 6) { whitePiece = new Bishop(col, 7, WHITE); blackPiece = new Bishop(col, 0, BLACK); }
            else if (i < 7) { whitePiece = new Queen(col, 7, WHITE);  blackPiece = new Queen(col, 0, BLACK); }
            else            { whitePiece = new King(col, 7, WHITE);   blackPiece = new King(col, 0, BLACK); }
            pieces.add(whitePiece);
            pieces.add(blackPiece);
        }
    }

    // Helper method for the purpose of moving a piece
    public void copyPieces(ArrayList<Piece> src, ArrayList<Piece> tgt) {
        tgt.clear();
        tgt.addAll(src);
    }

    // Main game loop update method
    public void update(boolean mouseJustPressed, boolean mouseJustReleased) {
        if (promotion) {
            // Handle pawn promotion selection
            promote();
            return;
        }

        if (gameOver || stalemate) {
            return;
        }

        if (activeP == null) {
            if (mouseJustPressed) {
                // Select a piece when the player clicks on it
                selectActivePiece();
            }
        } else {
            if (mouse.pressed) {
                if (mouseJustPressed) {
                    int mouseCol = mouse.x / Board.SIZE;
                    int mouseRow = mouse.y / Board.SIZE;
                    // Get the piece located on a specific square
                    Piece clickedPiece = getPieceAt(mouseCol, mouseRow);

                    if (clickedPiece == activeP) {
                        // Cancel the current move and restore the board state
                        cancelMove();
                    } else if (clickedPiece != null && clickedPiece.color == currentColor) {
                        activeP.resetPosition();
                        activeP = clickedPiece;
                        canMove = false;
                        validSquare = false;
                        // Update all legal move highlights for the selected piece
                        refreshLegalMoveSquares();
                    } else {
                        // Simulate a move before committing it
                        simulate();
                    }
                } else {
                    // Simulate a move before committing it
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
                        // Finalize and commit a valid move
                        commitMove();
                    } else {
                        // Cancel the current move and restore the board state
                        cancelMove();
                    }
                }
            }
        }
    }

    // Select a piece when the player clicks on it
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
                // Update all legal move highlights for the selected piece
                refreshLegalMoveSquares();
                return;
            }
        }
    }

    // Finalize and commit a valid move
    private void commitMove() {
        copyPieces(simPieces, pieces);
        activeP.updatePosition();
        legalMoveSquares.clear();

        if (castlingP != null) {
            castlingP.updatePosition();
        }

        // Check whether a pawn can be promoted
        if (canPromote()) {
            promotion = true;
        } else {
            // Complete the current turn and check game state
            finishTurn();
        }
    }

    // Cancel the current move and restore the board state
    private void cancelMove() {
        copyPieces(pieces, simPieces);
        activeP.resetPosition();
        activeP = null;
        castlingP = null;
        canMove = false;
        validSquare = false;
        legalMoveSquares.clear();
    }

    // Handle pawn promotion selection
    private void promote() {
        if (mouse.pressed) {
            for (Piece p : promoPieces) {
                if (p.col == mouse.x / Board.SIZE && p.row == mouse.y / Board.SIZE) {
                    switch (p.type) {
                        case "ROOK":   simPieces.add(new Rook(activeP.col, activeP.row, currentColor));   break;
                        case "KNIGHT": simPieces.add(new Knight(activeP.col, activeP.row, currentColor)); break;
                        case "BISHOP": simPieces.add(new Bishop(activeP.col, activeP.row, currentColor)); break;
                        default:       simPieces.add(new Queen(activeP.col, activeP.row, currentColor));  break;
                    }
                    simPieces.remove(activeP.getIndex());
                    copyPieces(simPieces, pieces);
                    activeP = null;
                    promotion = false;
                    canMove = false;
                    validSquare = false;
                    legalMoveSquares.clear();
                    // Complete the current turn and check game state
                    finishTurn();
                    return;
                }
            }
        }
    }

    // Simulate a move before committing it
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

            // Check if castling passes through a square under attack
            boolean castlingThroughCheck = isCastlingThroughCheck(activeP);
            // Move the rook when castling occurs
            checkCastling();

            if (!castlingThroughCheck && !isKingInCheck(currentColor)) {
                validSquare = true;
            }
        }
    }

    // Switch turns between white and black
    private void changePlayer() {
        currentColor = getOppositeColor(currentColor);
        for (Piece p : pieces) {
            if (p.color == currentColor) {
                p.twoStepped = false;
            }
        }
        activeP = null;
    }

    // Complete the current turn and check game state
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
            // Switch turns between white and black
            changePlayer();
        }
    }

    // Move the rook when castling occurs
    private void checkCastling() {
        if (castlingP != null) {
            castlingP.col = (castlingP.col == 0) ? 3 : 5;
            castlingP.x = castlingP.getX(castlingP.col);
        }
    }

    // Check whether a pawn can be promoted
    private boolean canPromote() {
        if ("PAWN".equals(activeP.type)) {
            if ((currentColor == WHITE && activeP.row == 0) || (currentColor == BLACK && activeP.row == 7)) {
                promoPieces.clear();
                promoPieces.add(new Rook(9, 2, currentColor));
                promoPieces.add(new Knight(9, 3, currentColor));
                promoPieces.add(new Bishop(9, 4, currentColor));
                promoPieces.add(new Queen(9, 5, currentColor));
                return true;
            }
        }
        return false;
    }

    // Verify that a square is inside the board boundaries
    public boolean isWithinBoard(int col, int row) {
        return col >= 0 && col < 8 && row >= 0 && row < 8;
    }

    // Find and return the king of the specified color
    private Piece getKing(int color) {
        for (Piece p : simPieces) {
            if ("KING".equals(p.type) && p.color == color) {
                return p;
            }
        }
        return null;
    }

    // Find the piece currently checking a king
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

    // Determine whether a king is in check
    private boolean isKingInCheck(int kingColor) {
        return findCheckingPiece(kingColor) != null;
    }

    // Check if a square is attacked by a given color
    private boolean isSquareAttacked(int col, int row, int attackingColor) {
        for (Piece p : simPieces) {
            if (p.color == attackingColor && attacksSquare(p, col, row)) {
                return true;
            }
        }
        return false;
    }

    // Determine whether a piece attacks a specific square
    private boolean attacksSquare(Piece piece, int targetCol, int targetRow) {
        int colDiff = Math.abs(targetCol - piece.col);
        int rowDiff = Math.abs(targetRow - piece.row);

        switch (piece.type) {
            case "PAWN":
                int direction = piece.color == WHITE ? -1 : 1;
                return rowDiff == 1 && targetRow == piece.row + direction && colDiff == 1;
            case "KNIGHT":
                return (colDiff == 1 && rowDiff == 2) || (colDiff == 2 && rowDiff == 1);
            case "BISHOP":
                // Check if there are pieces blocking a path
                return colDiff == rowDiff && isPathClear(piece, targetCol, targetRow);
            case "ROOK":
                // Check if there are pieces blocking a path
                return (targetCol == piece.col || targetRow == piece.row) && isPathClear(piece, targetCol, targetRow);
            case "QUEEN":
                boolean straight = targetCol == piece.col || targetRow == piece.row;
                boolean diagonal = colDiff == rowDiff;
                // Check if there are pieces blocking a path
                return (straight || diagonal) && isPathClear(piece, targetCol, targetRow);
            case "KING":
                return colDiff <= 1 && rowDiff <= 1;
            default:
                return false;
        }
    }

    // Check if there are pieces blocking a path
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

    // Get the piece located on a specific square
    public Piece getPieceAt(int col, int row) {
        for (Piece p : simPieces) {
            if (p.col == col && p.row == row) {
                return p;
            }
        }
        return null;
    }

    // Check if castling passes through a square under attack
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

    // Determine whether a player has any legal moves available
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

    // Test whether a move is legal without committing it
    private boolean canLegallyMove(Piece piece, int targetCol, int targetRow) {
        int[] savedState = piece.saveState();
        Piece savedHittingP = piece.hittingP;
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

            // Check if castling passes through a square under attack
            boolean castlingThroughCheck = isCastlingThroughCheck(piece);
            Piece rook = castlingP;
            int savedRookCol = rook != null ? rook.col : 0;
            int savedRookX   = rook != null ? rook.x   : 0;

            // Move the rook when castling occurs
            checkCastling();
            legal = !castlingThroughCheck && !isKingInCheck(piece.color);

            if (rook != null) {
                rook.col = savedRookCol;
                rook.x   = savedRookX;
            }
        }

        piece.restoreState(savedState);
        piece.hittingP = savedHittingP;
        castlingP = savedCastlingP;
        copyPieces(pieces, simPieces);

        return legal;
    }

    // Update all legal move highlights for the selected piece
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

    // Get the opposite player's color
    public int getOppositeColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }
}