package com.jchess.model.piece;

import com.jchess.model.Piece;

public class Bishop extends Piece {
    // Constructor for Bishop piece
    public Bishop(int col, int row, int color) {
        super(col, row, color);
        this.type = PieceType.BISHOP;
        this.img = loadStyledPieceImage("bishop");
    }

    // Check if the bishop can move to the target position
    @Override
    public boolean canMove(int targetCol, int targetRow) {
        if (!isWithinBoard(targetCol, targetRow) || isSameSquare(targetCol, targetRow)) {
            return false;
        }

        if (Math.abs(targetCol - preCol) == Math.abs(targetRow - preRow)) {
            return isValidSquare(targetCol, targetRow)
                    && !pieceIsOnDiagonalLine(targetCol, targetRow);
        }

        return false;
    }
}
