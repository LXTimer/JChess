package piece;

public class Queen extends Piece {

    // Constructor for Queen piece
    public Queen(int col, int row, int color) {
        super(col, row, color);
        this.type = PieceType.QUEEN;
        this.img = getImage("/resources/pieces/" + (color == 0 ? "white" : "black") + "-queen");
    }
    // Check if the queen can move to the target position

    @Override
    public boolean canMove(int targetCol, int targetRow) {
        if (!isWithinBoard(targetCol, targetRow) || isSameSquare(targetCol, targetRow)) {
            return false;
        }

        boolean straight = targetCol == preCol || targetRow == preRow;
        boolean diagonal = Math.abs(targetCol - preCol) == Math.abs(targetRow - preRow);

        if (straight) {
            return isValidSquare(targetCol, targetRow)
                    && !pieceIsOnStraightLine(targetCol, targetRow);
        }

        if (diagonal) {
            return isValidSquare(targetCol, targetRow)
                    && !pieceIsOnDiagonalLine(targetCol, targetRow);
        }

        return false;
    }
}
