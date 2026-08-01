package com.jchess.util;

import com.jchess.model.piece.PieceType;

public class MoveRecord {

    /** Quality classification for a move, based on engine evaluation loss. */
    public enum MoveQuality {
        /** Loss < 20 cp — engine's top choice. */
        BEST,
        /** Loss 20–70 cp — solid, slightly suboptimal. */
        GOOD,
        /** Loss 70–150 cp — noticeable but not fatal. */
        INACCURACY,
        /** Loss 150–300 cp — significantly worsens the position. */
        MISTAKE,
        /** Loss ≥ 300 cp — a serious error that often changes the outcome. */
        BLUNDER,
        /** Evaluation data not yet available. */
        UNKNOWN
    }

    public final PieceType type;
    public final int fromCol, fromRow;
    public final int toCol, toRow;
    public final int color;
    public final boolean isCapture;
    public final boolean isCastling;
    public String san;
    public String promotionType;
    public Integer timeOutWinner; // null if not time-out, 0 for White, 1 for Black
    public int timeSpentSeconds; // time spent on this move in seconds

    // -----------------------------------------------------------------------
    // Analysis-mode metadata (populated by AnalysisModeController)
    // -----------------------------------------------------------------------

    /** Best engine evaluation (white perspective, centipawns) of the position BEFORE
     *  this move was played.  Positive = white has the advantage. */
    public Integer evalBeforeCp;

    /** Best engine evaluation (white perspective, centipawns) of the position AFTER
     *  this move was played.  Positive = white has the advantage. */
    public Integer evalAfterCp;

    /** Computed quality classification; updated as soon as both evals are known. */
    public MoveQuality quality = MoveQuality.UNKNOWN;

    public MoveRecord(PieceType type, int fromCol, int fromRow, int toCol, int toRow, int color, boolean isCapture, boolean isCastling, String san) {
        this.type = type;
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.color = color;
        this.isCapture = isCapture;
        this.isCastling = isCastling;
        this.san = san;
        this.timeOutWinner = null;
        this.timeSpentSeconds = 0;
    }

    /**
     * Recomputes {@link #quality} from the stored before/after evaluations.
     * <p>If either evaluation is missing, the quality is reset to {@link MoveQuality#UNKNOWN}.
     * <p>Loss is measured from the mover's perspective:
     *   <ul>
     *     <li>White mover: {@code loss = evalBeforeCp - evalAfterCp}</li>
     *     <li>Black mover: {@code loss = evalAfterCp - evalBeforeCp}</li>
     *   </ul>
     */
    public void recomputeQuality() {
        if (evalBeforeCp == null || evalAfterCp == null) {
            quality = MoveQuality.UNKNOWN;
            return;
        }

        int loss;
        if (color == 0) { // WHITE — GameManager.WHITE
            loss = evalBeforeCp - evalAfterCp;
        } else {          // BLACK — GameManager.BLACK
            loss = evalAfterCp - evalBeforeCp;
        }

        if (loss >= 300) {
            quality = MoveQuality.BLUNDER;
        } else if (loss >= 150) {
            quality = MoveQuality.MISTAKE;
        } else if (loss >= 70) {
            quality = MoveQuality.INACCURACY;
        } else if (loss >= 20) {
            quality = MoveQuality.GOOD;
        } else {
            quality = MoveQuality.BEST;
        }
    }
}