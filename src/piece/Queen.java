package piece;

public class Queen extends Piece {

    public Queen(int col, int row, int color) {
        super(col, row, color);
        this.type = "QUEEN";
        this.img = getImage("/resources/" + (color == 0 ? "white" : "black") + "-queen");
    }

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
