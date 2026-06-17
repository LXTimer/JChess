package piece;

import view.GameManager;

public class Pawn extends Piece {
    // Constructor for Pawn piece
    public Pawn(int col, int row, int color) {
        super(col, row, color);
        this.type = PieceType.PAWN;
        this.img = getImage("/resources/pieces/" + (color == 0 ? "white" : "black") + "-pawn");
    }
    // Check if the pawn can move to the target position

    @Override
    public boolean canMove(int targetCol, int targetRow) {
        if (!isWithinBoard(targetCol, targetRow) || isSameSquare(targetCol, targetRow)) {
            return false;
        }

        int direction = color == 0 ? -1 : 1;
        if (GameManager.isBoardFlipped) {
            direction = -direction;
        }
        hittingP = getHittingP(targetCol, targetRow);

        if (targetCol == preCol && targetRow == preRow + direction && hittingP == null) {
            return true;
        }

        if (targetCol == preCol && targetRow == preRow + direction * 2 && !moved) {
            return hittingP == null && getPieceAt(preCol, preRow + direction) == null;
        }

        if (Math.abs(targetCol - preCol) == 1 && targetRow == preRow + direction) {
            if (hittingP != null && hittingP.color != color) {
                return true;
            }

            Piece sidePawn = getPieceAt(targetCol, preRow);
            if (sidePawn != null
                    && sidePawn.color != color
                    && sidePawn.type == PieceType.PAWN
                    && sidePawn.twoStepped) {
                hittingP = sidePawn;
                return true;
            }
        }

        hittingP = null;
        return false;
    }
}
