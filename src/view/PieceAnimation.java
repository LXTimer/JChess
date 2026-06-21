package view;

import piece.Piece;

/**
 * Represents an animated piece movement from one square to another.
 */
public class PieceAnimation {
    
    public Piece piece;
    public int startCol, startRow;
    public int endCol, endRow;
    public double progress; // 0.0 to 1.0
    private static final double ANIMATION_DURATION_MS = 300.0; // 300ms animation
    private long startTime;
    
    public PieceAnimation(Piece piece, int startCol, int startRow, int endCol, int endRow) {
        this.piece = piece;
        this.startCol = startCol;
        this.startRow = startRow;
        this.endCol = endCol;
        this.endRow = endRow;
        this.progress = 0.0;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Updates the animation progress based on elapsed time.
     * @return true if animation is complete, false otherwise
     */
    public boolean update() {
        long elapsedMs = System.currentTimeMillis() - startTime;
        progress = Math.min(1.0, elapsedMs / ANIMATION_DURATION_MS);
        return progress >= 1.0;
    }
    
    /**
     * Gets the interpolated x position based on animation progress.
     */
    public int getAnimatedX() {
        double startX = startCol * model.Board.SIZE;
        double endX = endCol * model.Board.SIZE;
        return (int) (startX + (endX - startX) * easeInOutQuad(progress));
    }
    
    /**
     * Gets the interpolated y position based on animation progress.
     */
    public int getAnimatedY() {
        double startY = startRow * model.Board.SIZE;
        double endY = endRow * model.Board.SIZE;
        return (int) (startY + (endY - startY) * easeInOutQuad(progress));
    }
    
    /**
     * Easing function for smooth animation (ease in-out quadratic).
     */
    private double easeInOutQuad(double t) {
        if (t < 0.5) {
            return 2 * t * t;
        } else {
            return -1 + (4 - 2 * t) * t;
        }
    }
}
