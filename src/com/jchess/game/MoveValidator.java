package com.jchess.game;

import com.jchess.model.Board;
import com.jchess.model.Piece;
import com.jchess.model.piece.PieceType;
import java.awt.Point;
import java.util.ArrayList;

public class MoveValidator {
    public static final int WHITE = 0;
    public static final int BLACK = 1;

    private final GameManager gm;

    public MoveValidator(GameManager gm) {
        this.gm = gm;
    }

    public Piece findCheckingPiece(int kingColor) {
        Piece king = getKing(kingColor);
        if (king == null) return null;

        for (Piece p : GameManager.simPieces) {
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
        for (Piece p : GameManager.simPieces) {
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
            case PAWN:
                int direction = piece.color == WHITE ? -1 : 1;
                if (gm.boardFlipped) {
                    direction = -direction;
                }
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

    public Piece getPieceAt(int col, int row) {
        for (Piece p : GameManager.simPieces) {
            if (p.col == col && p.row == row) {
                return p;
            }
        }
        return null;
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

    public boolean hasLegalMove(int color) {
        Piece savedCastlingP = GameManager.castlingP;

        for (Piece piece : new ArrayList<>(gm.pieces)) {
            if (piece.color != color) {
                continue;
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (canLegallyMove(piece, col, row)) {
                        GameManager.castlingP = savedCastlingP;
                        gm.copyPieces(gm.pieces, GameManager.simPieces);
                        return true;
                    }
                }
            }
        }

        GameManager.castlingP = savedCastlingP;
        gm.copyPieces(gm.pieces, GameManager.simPieces);
        return false;
    }

    public boolean canLegallyMove(Piece piece, int targetCol, int targetRow) {
        int[] savedState = piece.saveState();
        Piece savedHittingP = piece.hittingP;
        Piece savedCastlingP = GameManager.castlingP;

        gm.copyPieces(gm.pieces, GameManager.simPieces);
        GameManager.castlingP = null;

        piece.col = targetCol;
        piece.row = targetRow;

        boolean legal = false;

        if (piece.canMove(targetCol, targetRow)) {
            if (piece.hittingP != null) {
                int hitIndex = piece.hittingP.getIndex();
                if (hitIndex >= 0) {
                    GameManager.simPieces.remove(hitIndex);
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
        GameManager.castlingP = savedCastlingP;
        gm.copyPieces(gm.pieces, GameManager.simPieces);

        return legal;
    }

    public void refreshLegalMoveSquares() {
        gm.legalMoveSquares.clear();

        if (gm.activeP == null) {
            return;
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (canLegallyMove(gm.activeP, col, row)) {
                    gm.legalMoveSquares.add(new Point(col, row));
                }
            }
        }

        gm.copyPieces(gm.pieces, GameManager.simPieces);
    }

    public void checkCastling() {
        if (GameManager.castlingP != null) {
            GameManager.castlingP.col = (GameManager.castlingP.col == 0) ? 3 : 5;
            GameManager.castlingP.x = GameManager.castlingP.getX(GameManager.castlingP.col);
        }
    }

    public boolean canPromote() {
        if (gm.activeP != null && gm.activeP.type == PieceType.PAWN) {
            if ((gm.currentColor == WHITE && gm.activeP.row == 0) || (gm.currentColor == BLACK && gm.activeP.row == 7)) {
                gm.promoPieces.clear();
                gm.promoPieces.add(new com.jchess.model.piece.Rook(9, 2, gm.currentColor));
                gm.promoPieces.add(new com.jchess.model.piece.Knight(9, 3, gm.currentColor));
                gm.promoPieces.add(new com.jchess.model.piece.Bishop(9, 4, gm.currentColor));
                gm.promoPieces.add(new com.jchess.model.piece.Queen(9, 5, gm.currentColor));
                return true;
            }
        }
        return false;
    }

    public static int getOppositeColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }

    public boolean isWithinBoard(int col, int row) {
        return col >= 0 && col < 8 && row >= 0 && row < 8;
    }

    public boolean isInsufficientMaterial() {
        boolean whiteHasSufficient = false;
        boolean blackHasSufficient = false;

        for (Piece p : gm.pieces) {
            if (p.type == PieceType.PAWN || p.type == PieceType.ROOK || p.type == PieceType.QUEEN) {
                if (p.color == WHITE) {
                    whiteHasSufficient = true;
                } else {
                    blackHasSufficient = true;
                }
            } else if (p.type == PieceType.BISHOP || p.type == PieceType.KNIGHT) {
                if (p.color == WHITE) {
                    whiteHasSufficient = true;
                } else {
                    blackHasSufficient = true;
                }
            }
        }

        gm.stalemate = !whiteHasSufficient && !blackHasSufficient;
        return !whiteHasSufficient && !blackHasSufficient;
    }

    private Piece getKing(int color) {
        for (Piece p : GameManager.simPieces) {
            if (p.type == PieceType.KING && p.color == color) {
                return p;
            }
        }
        return null;
    }
}