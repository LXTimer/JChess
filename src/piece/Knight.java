package piece;

public class Knight extends Piece {
    // Constructor for Knight piece
    public Knight(int col, int row, int color) {
        super(col, row, color);
        this.type = "KNIGHT";
        this.img = getImage("/resources/" + (color == 0 ? "white" : "black") + "-knight");
    }

    @Override
    public boolean canMove(int targetCol, int targetRow) {
        if (!isWithinBoard(targetCol, targetRow) || isSameSquare(targetCol, targetRow)) {
            return false;
        }

        int colDiff = Math.abs(targetCol - preCol);
        int rowDiff = Math.abs(targetRow - preRow);
        return ((colDiff == 1 && rowDiff == 2) || (colDiff == 2 && rowDiff == 1))
                && isValidSquare(targetCol, targetRow);
    }
}
