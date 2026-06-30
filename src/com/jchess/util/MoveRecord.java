package com.jchess.util;

import com.jchess.model.piece.PieceType;

public class MoveRecord {
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
}
