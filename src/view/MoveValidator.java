package view;

import model.BoardState;
import piece.Piece;
import piece.PieceType;
import java.util.ArrayList;

public class MoveValidator {
    public static final int WHITE = 0;
    public static final int BLACK = 1;

    private final ArrayList<Piece> pieces;
    private final BoardState boardState;

    public MoveValidator(ArrayList<Piece> pieces, BoardState boardState) {
        this.pieces = pieces;
        this.boardState = boardState;
    }

    public Piece findCheckingPiece(int kingColor) {
        Piece king = getKing(kingColor);
        if (king == null) return null;

        for (Piece p : boardState.getAllPieces()) {
            if (p.color != kingColor && attacksSquare(p, king.col, king.row)) {
                return p;
            }
        }
        return null;
    }

    public boolean isKingInCheck(int kingColor) {
        return findCheckingPiece(kingColor) != null;
    }

    public boolean isSquareAttacked(int col, int row, int attackingColor) {
        for (Piece p : boardState.getAllPieces()) {
            if (p.color == attackingColor && attacksSquare(p, col, row)) {
                return true;
            }
        }
        return false;
    }

    public boolean attacksSquare(Piece piece, int targetCol, int targetRow) {
        int colDiff = Math.abs(targetCol - piece.col);
        int rowDiff = Math.abs(targetRow - piece.row);

        switch (piece.type) {
            case PAWN:
                int direction = piece.color == WHITE ? -1 : 1;
                return rowDiff == 1 && targetRow == piece.row + direction && colDiff == 1;
            case KNIGHT:
                return (colDiff == 1 && rowDiff == 2) || (colDiff == 2 && rowDiff == 1);
            case BISHOP:
                return colDiff == rowDiff && isPathClear(piece, targetCol, targetRow);
            case ROOK:
                return (targetCol == piece.col || targetRow == piece.row) && isPathClear(piece, targetCol, targetRow);
            case QUEEN:
                boolean straight = targetCol == piece.col || targetRow == piece.row;
                boolean diagonal = colDiff == rowDiff;
                return (straight || diagonal) && isPathClear(piece, targetCol, targetRow);
            case KING:
                return colDiff <= 1 && rowDiff <= 1;
            default:
                return false;
        }
    }

    public boolean isPathClear(Piece piece, int targetCol, int targetRow) {
        int colDirection = Integer.compare(targetCol, piece.col);
        int rowDirection = Integer.compare(targetRow, piece.row);
        int currentCol = piece.col + colDirection;
        int currentRow = piece.row + rowDirection;

        while (currentCol != targetCol || currentRow != targetRow) {
            if (boardState.getPieceAt(currentCol, currentRow) != null) {
                return false;
            }
            currentCol += colDirection;
            currentRow += rowDirection;
        }

        return true;
    }

    public boolean isCastlingThroughCheck(Piece king) {
        if (king == null || king.type != PieceType.KING || Math.abs(king.col - king.preCol) != 2) {
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

    public boolean hasLegalMove(int color, Piece savedCastlingP) {
        for (Piece piece : new ArrayList<>(pieces)) {
            if (piece.color != color) {
                continue;
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (canLegallyMove(piece, col, row, savedCastlingP)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean canLegallyMove(Piece piece, int targetCol, int targetRow, Piece savedCastlingP) {
        int[] savedState = piece.saveState();
        Piece savedHittingP = piece.hittingP;
        Piece savedCastlingP2 = GameManager.castlingP;

        boardState.copy(new BoardState(pieces));
        GameManager.castlingP = null;

        piece.col = targetCol;
        piece.row = targetRow;

        boolean legal = false;

        if (piece.canMove(targetCol, targetRow)) {
            if (piece.hittingP != null) {
                int hitIndex = -1;
                for (int i = 0; i < boardState.getAllPieces().size(); i++) {
                    if (boardState.getAllPieces().get(i) == piece.hittingP) {
                        hitIndex = i;
                        break;
                    }
                }
                if (hitIndex >= 0) {
                    boardState.removePiece(piece.hittingP);
                }
            }

            boolean castlingThroughCheck = isCastlingThroughCheck(piece);
            Piece rook = GameManager.castlingP;
            int savedRookCol = rook != null ? rook.col : 0;
            int savedRookX   = rook != null ? rook.x   : 0;

            checkCastling();
            legal = !castlingThroughCheck && !isKingInCheck(piece.color);

            if (rook != null) {
                rook.col = savedRookCol;
                rook.x   = savedRookX;
            }
        }

        piece.restoreState(savedState);
        piece.hittingP = savedHittingP;
        GameManager.castlingP = savedCastlingP2;
        boardState.copy(new BoardState(pieces));

        return legal;
    }

    private void checkCastling() {
        if (GameManager.castlingP != null) {
            GameManager.castlingP.col = (GameManager.castlingP.col == 0) ? 3 : 5;
            GameManager.castlingP.x = GameManager.castlingP.getX(GameManager.castlingP.col);
        }
    }

    private Piece getKing(int color) {
        for (Piece p : boardState.getAllPieces()) {
            if (p.type == PieceType.KING && p.color == color) {
                return p;
            }
        }
        return null;
    }

    public static int getOppositeColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }
}
