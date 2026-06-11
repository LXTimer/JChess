package piece;

public class Bishop extends Piece {

    public Bishop(int col, int row, int color) {
        super(col, row, color);
        this.type = "BISHOP";
        this.img = getImage("/resources/" + (color == 0 ? "white" : "black") + "-bishop");
    }

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
