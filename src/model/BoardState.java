package model;

import piece.Piece;
import piece.PieceType;
import java.util.ArrayList;

public class BoardState {
    private Piece[][] boardGrid = new Piece[8][8];
    private ArrayList<Piece> allPieces = new ArrayList<>();

    public BoardState(ArrayList<Piece> pieces) {
        reset(pieces);
    }

    public void reset(ArrayList<Piece> pieces) {
        allPieces.clear();
        clearGrid();
        for (Piece p : pieces) {
            allPieces.add(p);
            updateGridPosition(p);
        }
    }

    private void clearGrid() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                boardGrid[row][col] = null;
            }
        }
    }

    public void updatePiecePosition(Piece piece, int newCol, int newRow) {
        // Remove from old position
        if (piece.col >= 0 && piece.col < 8 && piece.row >= 0 && piece.row < 8) {
            boardGrid[piece.row][piece.col] = null;
        }
        
        // Update piece coordinates
        piece.col = newCol;
        piece.row = newRow;
        
        // Place at new position
        updateGridPosition(piece);
    }

    private void updateGridPosition(Piece piece) {
        if (piece.col >= 0 && piece.col < 8 && piece.row >= 0 && piece.row < 8) {
            boardGrid[piece.row][piece.col] = piece;
        }
    }

    public Piece getPieceAt(int col, int row) {
        if (col < 0 || col >= 8 || row < 0 || row >= 8) {
            return null;
        }
        return boardGrid[row][col];
    }

    public void addPiece(Piece piece) {
        if (!allPieces.contains(piece)) {
            allPieces.add(piece);
            updateGridPosition(piece);
        }
    }

    public void removePiece(Piece piece) {
        allPieces.remove(piece);
        if (piece.col >= 0 && piece.col < 8 && piece.row >= 0 && piece.row < 8) {
            boardGrid[piece.row][piece.col] = null;
        }
    }

    public ArrayList<Piece> getAllPieces() {
        return new ArrayList<>(allPieces);
    }

    public ArrayList<Piece> getPiecesByColor(int color) {
        ArrayList<Piece> result = new ArrayList<>();
        for (Piece p : allPieces) {
            if (p.color == color) {
                result.add(p);
            }
        }
        return result;
    }

    public Piece getKing(int color) {
        for (Piece p : allPieces) {
            if (p.type == PieceType.KING && p.color == color) {
                return p;
            }
        }
        return null;
    }

    public void copy(BoardState source) {
        allPieces.clear();
        clearGrid();
        for (Piece p : source.allPieces) {
            allPieces.add(p);
            updateGridPosition(p);
        }
    }
}
