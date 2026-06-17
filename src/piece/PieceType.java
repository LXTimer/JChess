package piece;

public enum PieceType {
    PAWN(1, "pawn"),
    KNIGHT(3, "knight"),
    BISHOP(3, "bishop"),
    ROOK(5, "rook"),
    QUEEN(9, "queen"),
    KING(0, "king");

    private final int value;
    private final String resourceName;

    PieceType(int value, String resourceName) {
        this.value = value;
        this.resourceName = resourceName;
    }

    public int getValue() {
        return value;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getNotation() {
        switch (this) {
            case PAWN:
                return "";
            case KNIGHT:
                return "N";
            case BISHOP:
                return "B";
            case ROOK:
                return "R";
            case QUEEN:
                return "Q";
            case KING:
                return "K";
            default:
                return "";
        }
    }

    public static PieceType fromString(String str) {
        switch (str.toUpperCase()) {
            case "PAWN":
                return PAWN;
            case "KNIGHT":
                return KNIGHT;
            case "BISHOP":
                return BISHOP;
            case "ROOK":
                return ROOK;
            case "QUEEN":
                return QUEEN;
            case "KING":
                return KING;
            default:
                throw new IllegalArgumentException("Unknown piece type: " + str);
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
