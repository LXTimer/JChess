package piece;

import view.GameManager;

public class King extends Piece {
    // Constructor for King piece
    public King(int col, int row, int color) {
        super(col, row, color);
        this.type = PieceType.KING;
        this.img = getImage("/resources/pieces/" + (color == 0 ? "white" : "black") + "-king");
    }

    @Override
    public boolean canMove(int targetCol, int targetRow) {
        if (!isWithinBoard(targetCol, targetRow) || isSameSquare(targetCol, targetRow)) {
            return false;
        }

        int colDiff = Math.abs(targetCol - preCol);
        int rowDiff = Math.abs(targetRow - preRow);

        if (colDiff <= 1 && rowDiff <= 1) {
            return isValidSquare(targetCol, targetRow);
        }

        if (!moved && rowDiff == 0 && colDiff == 2) {
            return canCastle(targetCol);
        }

        return false;
    }

    private boolean canCastle(int targetCol) {
        int rookCol = targetCol > preCol ? 7 : 0;
        int direction = targetCol > preCol ? 1 : -1;
        Piece rook = getPieceAt(rookCol, preRow);

        if (rook == null || rook.color != color || rook.type != PieceType.ROOK || rook.moved) {
            return false;
        }

        for (int col = preCol + direction; col != rookCol; col += direction) {
            if (getPieceAt(col, preRow) != null) {
                return false;
            }
        }

        GameManager.castlingP = rook;
        return true;
    }
}
