package view;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Caches rendered game elements to avoid expensive recalculation every frame.
 * Only regenerates when necessary.
 */
public class RenderCache {
    private BufferedImage moveLogCache;
    private BufferedImage capturedPiecesCache;
    
    private boolean moveLogDirty = true;
    private boolean capturedPiecesDirty = true;
    
    private int lastMoveCount = -1;
    private int lastScrollLine = -1;
    private int lastWhiteCapturedValue = -1;
    private int lastBlackCapturedValue = -1;

    public RenderCache() {
    }

    public void invalidateMoveLog() {
        moveLogDirty = true;
    }

    public void invalidateCapturedPieces() {
        capturedPiecesDirty = true;
    }

    public void invalidateAll() {
        moveLogDirty = true;
        capturedPiecesDirty = true;
    }

    public boolean isMoveLogDirty(int moveCount, int scrollLine) {
        if (moveLogDirty || lastMoveCount != moveCount || lastScrollLine != scrollLine) {
            lastMoveCount = moveCount;
            lastScrollLine = scrollLine;
            moveLogDirty = false;
            return true;
        }
        return false;
    }

    public boolean isCapturedPiecesDirty(int whiteCapturedValue, int blackCapturedValue) {
        if (capturedPiecesDirty || lastWhiteCapturedValue != whiteCapturedValue || 
            lastBlackCapturedValue != blackCapturedValue) {
            lastWhiteCapturedValue = whiteCapturedValue;
            lastBlackCapturedValue = blackCapturedValue;
            capturedPiecesDirty = false;
            return true;
        }
        return false;
    }

    public void setMoveLogCache(BufferedImage cache) {
        this.moveLogCache = cache;
    }

    public BufferedImage getMoveLogCache() {
        return moveLogCache;
    }

    public void setCapturedPiecesCache(BufferedImage cache) {
        this.capturedPiecesCache = cache;
    }

    public BufferedImage getCapturedPiecesCache() {
        return capturedPiecesCache;
    }
}
