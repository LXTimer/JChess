package view;

public class MoveRecord {
    public final String type;
    public final int fromCol, fromRow;
    public final int toCol, toRow;
    public final int color;
    public final boolean isCapture;
    public final boolean isCastling;
    public String san;
    public String promotionType;

    public MoveRecord(String type, int fromCol, int fromRow, int toCol, int toRow, int color, boolean isCapture, boolean isCastling, String san) {
        this.type = type;
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.color = color;
        this.isCapture = isCapture;
        this.isCastling = isCastling;
        this.san = san;
    }
}
