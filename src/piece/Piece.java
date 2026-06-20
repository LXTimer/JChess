package piece;

import view.GameManager;
import model.Board;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class Piece {

    // Declare essential attributes for a chess piece
    public PieceType type;
    public BufferedImage img;
    public int x, y;
    public int col, row, preCol, preRow;
    public int color;
    public Piece hittingP;
    public boolean moved, twoStepped;

    // Constrcutor
    public Piece(int col, int row, int color) {
        this.col = col;
        this.row = row;
        this.color = color;
        this.preCol = col;
        this.preRow = row;
        updatePixelPosition();
    }

    // Method to load piece images
    public BufferedImage getImage(String path) {
        try {
            return ImageIO.read(
                    Objects.requireNonNull(Piece.class.getResourceAsStream(path + ".png")));
        } catch (Exception e) {
            System.err.println("Failed to load image: " + path);
            return null;
        }
    }

    // Methods to convert between board coordinates and pixel coordinates
    public int getX(int col) {
        return col * Board.SIZE;
    }

    public int getY(int row) {
        return row * Board.SIZE;
    }

    public int getCol(int x) {
        return (x + Board.SIZE / 2) / Board.SIZE;
    }

    public int getRow(int y) {
        return (y + Board.SIZE / 2) / Board.SIZE;
    }

    protected void updatePixelPosition() {
        this.x = getX(col);
        this.y = getY(row);
    }

    public Piece getPieceAt(int targetCol, int targetRow) {
        return GameManager.simPieces.stream()
                .filter(p -> p.col == targetCol && p.row == targetRow && p != this)
                .findFirst()
                .orElse(null);
    }

    // Method to check if there's a piece at the target position
    public Piece getHittingP(int targetCol, int targetRow) {
        return getPieceAt(targetCol, targetRow);
    }

    public int getIndex() {
        return GameManager.simPieces.indexOf(this);
    }

    public void draw(Graphics2D g2) {
        if (img != null) {
            g2.drawImage(img, x, y, Board.SIZE, Board.SIZE, null);
        }
    }

    public void updatePosition() {
        if (type == PieceType.PAWN && Math.abs(row - preRow) == 2) {
            twoStepped = true;
        }

        updatePixelPosition();
        preCol = col;
        preRow = row;
        moved = true;
    }

    public boolean canMove(int targetCol, int targetRow) {
        return false;
    }

    public boolean isWithinBoard(int targetCol, int targetRow) {
        return targetCol >= 0 && targetCol <= 7 && targetRow >= 0 && targetRow <= 7;
    }

    public void resetPosition() {
        col = preCol;
        row = preRow;
        updatePixelPosition();
    }

    public boolean isValidSquare(int targetCol, int targetRow) {
        hittingP = getHittingP(targetCol, targetRow);

        if (hittingP == null) {
            return true;
        }

        if (hittingP.color != this.color) {
            return true;
        }

        hittingP = null;
        return false;
    }

    public boolean isSameSquare(int targetCol, int targetRow) {
        return targetCol == preCol && targetRow == preRow;
    }

    public boolean pieceIsOnStraightLine(int targetCol, int targetRow) {
        int colDirection = Integer.compare(targetCol, preCol);
        int rowDirection = Integer.compare(targetRow, preRow);

        if (colDirection != 0 && rowDirection != 0) {
            return false;
        }

        int currentCol = preCol + colDirection;
        int currentRow = preRow + rowDirection;

        while (currentCol != targetCol || currentRow != targetRow) {
            for (Piece p : GameManager.simPieces) {
                if (p.col == currentCol && p.row == currentRow) {
                    hittingP = p;
                    return true;
                }
            }
            currentCol += colDirection;
            currentRow += rowDirection;
        }

        return false;
    }

    public boolean pieceIsOnDiagonalLine(int targetCol, int targetRow) {
        int colDirection = Integer.compare(targetCol, preCol);
        int rowDirection = Integer.compare(targetRow, preRow);

        if (Math.abs(targetCol - preCol) != Math.abs(targetRow - preRow)) {
            return false;
        }

        int currentCol = preCol + colDirection;
        int currentRow = preRow + rowDirection;

        while (currentCol != targetCol && currentRow != targetRow) {
            for (Piece p : GameManager.simPieces) {
                if (p.col == currentCol && p.row == currentRow) {
                    hittingP = p;
                    return true;
                }
            }
            currentCol += colDirection;
            currentRow += rowDirection;
        }

        return false;
    }

    // Save current position state for simulation
    public int[] saveState() {
        return new int[]{col, row, x, y};
    }

    // Restore position state after simulation
    public void restoreState(int[] state) {
        col = state[0];
        row = state[1];
        x   = state[2];
        y   = state[3];
    }

    // Make a deep copy of this piece for undo history snapshots
    public Piece copy() {
        Piece clone;
        switch (type) {
            case PAWN:
                clone = new Pawn(col, row, color);
                break;
            case KNIGHT:
                clone = new Knight(col, row, color);
                break;
            case BISHOP:
                clone = new Bishop(col, row, color);
                break;
            case ROOK:
                clone = new Rook(col, row, color);
                break;
            case QUEEN:
                clone = new Queen(col, row, color);
                break;
            case KING:
                clone = new King(col, row, color);
                break;
            default:
                clone = new Piece(col, row, color);
                break;
        }

        clone.preCol = preCol;
        clone.preRow = preRow;
        clone.col = col;
        clone.row = row;
        clone.updatePixelPosition();
        clone.hittingP = null;
        clone.moved = moved;
        clone.twoStepped = twoStepped;
        return clone;
    }

    @Override
    public String toString() {
        String colorName = (color == 0) ? "White" : "Black";
        return colorName + " " + type;
    }
}