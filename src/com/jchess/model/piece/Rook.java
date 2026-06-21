package com.jchess.model.piece;

import com.jchess.model.Piece;

public class Rook extends Piece {

    // Constructor for Rook piece
    public Rook(int col, int row, int color) {
        super(col, row, color);
        this.type = PieceType.ROOK;
        this.img = getImage("/resources/pieces/" + (color == 0 ? "white" : "black") + "-rook");
    }
    // Check if the rook can move to the target position
    @Override
    public boolean canMove(int targetCol, int targetRow) {
        if (!isWithinBoard(targetCol, targetRow) || isSameSquare(targetCol, targetRow)) {
            return false;
        }

        if (targetCol == preCol || targetRow == preRow) {
            return isValidSquare(targetCol, targetRow)
                    && !pieceIsOnStraightLine(targetCol, targetRow);
        }

        return false;
    }
}
