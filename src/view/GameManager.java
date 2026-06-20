package view;

import controller.Mouse;
import java.awt.Point;
import java.util.ArrayList;
import java.awt.event.*;

import model.Board;
import model.BoardState;
import piece.Bishop;
import piece.King;
import piece.Knight;
import piece.Pawn;
import piece.Piece;
import piece.PieceType;
import piece.Queen;
import piece.Rook;

public class GameManager {

    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public static boolean isBoardFlipped = false;

    // Declare all the variables
    public ArrayList<Piece> pieces = new ArrayList<>();
    public static ArrayList<Piece> simPieces = new ArrayList<>();
    public ArrayList<Piece> promoPieces = new ArrayList<>();
    public ArrayList<Point> legalMoveSquares = new ArrayList<>();
    public ArrayList<MoveRecord> moves = new ArrayList<>();
    public ArrayList<Piece> capturedPieces = new ArrayList<>();
    public int scrollStartLine = 0;

    private final ArrayList<ArrayList<Piece>> piecesHistory = new ArrayList<>();
    private final ArrayList<ArrayList<Piece>> simPiecesHistory = new ArrayList<>();
    private final ArrayList<ArrayList<Piece>> capturedPiecesHistory = new ArrayList<>();
    private final ArrayList<Integer> currentColorHistory = new ArrayList<>();
    private final ArrayList<Boolean> gameOverHistory = new ArrayList<>();
    private final ArrayList<Boolean> stalemateHistory = new ArrayList<>();
    private final ArrayList<Boolean> whiteResignHistory = new ArrayList<>();
    private final ArrayList<Boolean> blackResignHistory = new ArrayList<>();
    private final ArrayList<Boolean> boardFlippedHistory = new ArrayList<>();
    private ArrayList<Piece> preMoveSnapshot; // Snapshot for undoing to pre-move state, separate from piecesHistory which snapshots after move is committed

    public static Piece castlingP;
    public Piece activeP;
    public Piece checkingP;

    public int currentColor;

    public boolean canMove;
    public boolean validSquare;
    public boolean promotion;
    public boolean gameOver;
    public boolean stalemate;
    public boolean boardFlipped = false;
    public boolean whiteResign = false;
    public boolean blackResign = false;

    private final Mouse mouse;
    
    // Caches for optimization
    private ArrayList<Piece> cachedSortedWhite;
    private ArrayList<Piece> cachedSortedBlack;
    private int cachedCapturedCount = -1;

    public GameManager() {
        this.mouse = new Mouse();
        setPieces();
        copyPieces(pieces, simPieces);
    }

    // Constructor
    public GameManager(Mouse mouse) {
        this.mouse = mouse;
        setPieces();
        copyPieces(pieces, simPieces);
    }

    // Method that sets up all the pieces in the beginning
    public void setPieces() {
        pieces.clear();
        moves.clear();
        capturedPieces.clear();
        scrollStartLine = 0;

        for (int col = 0; col < 8; col++) {
            pieces.add(new Pawn(col, 6, WHITE));
            pieces.add(new Pawn(col, 1, BLACK));
        }

        int[] backRankCols = {0, 7, 1, 6, 2, 5, 3, 4};
        for (int i = 0; i < backRankCols.length; i++) {
            int col = backRankCols[i];
            Piece whitePiece, blackPiece;
            if      (i < 2) { whitePiece = new Rook(col, 7, WHITE);   blackPiece = new Rook(col, 0, BLACK); }
            else if (i < 4) { whitePiece = new Knight(col, 7, WHITE); blackPiece = new Knight(col, 0, BLACK); }
            else if (i < 6) { whitePiece = new Bishop(col, 7, WHITE); blackPiece = new Bishop(col, 0, BLACK); }
            else if (i < 7) { whitePiece = new Queen(col, 7, WHITE);  blackPiece = new Queen(col, 0, BLACK); }
            else            { whitePiece = new King(col, 7, WHITE);   blackPiece = new King(col, 0, BLACK); }
            pieces.add(whitePiece);
            pieces.add(blackPiece);
        }
    }

    // Helper method for the purpose of moving a piece
    public void copyPieces(ArrayList<Piece> src, ArrayList<Piece> tgt) {
        tgt.clear();
        tgt.addAll(src);
    }

    private ArrayList<Piece> copyPieceList(ArrayList<Piece> src) {
        ArrayList<Piece> clone = new ArrayList<>();
        for (Piece p : src) {
            clone.add(p.copy());
        }
        return clone;
    }

    // Capture the board state right before the currently selected piece (and
    // any castling rook) starts moving, so undo can restore to this exact
    // pre-move position later. Must be called at selection time, not at
    // commit time, since pieces/simPieces share the same Piece instances.
    private void capturePreMoveSnapshot() {
        preMoveSnapshot = copyPieceList(pieces);
    }

    private void saveHistorySnapshot() {
        piecesHistory.add(preMoveSnapshot != null ? preMoveSnapshot : copyPieceList(pieces));
        simPiecesHistory.add(copyPieceList(pieces));
        capturedPiecesHistory.add(copyPieceList(capturedPieces));
        currentColorHistory.add(currentColor);
        gameOverHistory.add(gameOver);
        stalemateHistory.add(stalemate);
        whiteResignHistory.add(whiteResign);
        blackResignHistory.add(blackResign);
        boardFlippedHistory.add(boardFlipped);
        preMoveSnapshot = null;
    }

    public boolean canUndo() {
        return !moves.isEmpty() && !piecesHistory.isEmpty();
    }

    public void undoLastMove() {
        if (!canUndo()) {
            return;
        }

        int lastIndex = piecesHistory.size() - 1;
        pieces = piecesHistory.remove(lastIndex);
        copyPieces(pieces, simPieces);
        capturedPieces = capturedPiecesHistory.remove(lastIndex);
        currentColor = currentColorHistory.remove(lastIndex);
        gameOver = gameOverHistory.remove(lastIndex);
        stalemate = stalemateHistory.remove(lastIndex);
        whiteResign = whiteResignHistory.remove(lastIndex);
        blackResign = blackResignHistory.remove(lastIndex);
        boardFlipped = boardFlippedHistory.remove(lastIndex);
        isBoardFlipped = boardFlipped;

        if (!simPiecesHistory.isEmpty()) {
            simPiecesHistory.remove(simPiecesHistory.size() - 1);
        }

        if (!moves.isEmpty()) {
            moves.remove(moves.size() - 1);
        }

        activeP = null;
        legalMoveSquares.clear();
        promotion = false;
        promoPieces.clear();
        castlingP = null;
        checkingP = null;
        preMoveSnapshot = null;
        cachedSortedWhite = null;
        cachedSortedBlack = null;
        cachedCapturedCount = capturedPieces.size();

        // Snap back to live view after undo
        viewMoveIndex = -1;

        if (!gameOver && !stalemate) {
            checkingP = findCheckingPiece(getOppositeColor(currentColor));
        }

        scrollToBottom();
    }

    // Main game loop update method
    public void update(boolean mouseJustPressed, boolean mouseJustReleased) {
        if (promotion) {
            // Handle pawn promotion selection
            promote();
            return;
        }

        if (gameOver || stalemate) {
            return;
        }

        if (activeP == null) {
            if (mouseJustPressed) {
                // Select a piece when the player clicks on it
                selectActivePiece();
            }
        } else {
            if (mouse.pressed) {
                if (mouseJustPressed) {
                    int mouseCol = getMouseColOnBoard();
                    int mouseRow = getMouseRowOnBoard();
                    // Get the piece located on a specific square
                    Piece clickedPiece = getPieceAt(mouseCol, mouseRow);

                    if (clickedPiece == activeP) {
                        // Cancel the current move and restore the board state
                        cancelMove();
                    } else if (clickedPiece != null && clickedPiece.color == currentColor) {
                        activeP.resetPosition();
                        activeP = clickedPiece;
                        canMove = false;
                        validSquare = false;
                        // Re-snapshot now that a different piece is selected,
                        // since the board is back in a settled (pre-drag) state
                        capturePreMoveSnapshot();
                        // Update all legal move highlights for the selected piece
                        refreshLegalMoveSquares();
                    } else {
                        // Simulate a move before committing it
                        simulate();
                    }
                } else {
                    // Simulate a move before committing it
                    simulate();
                }
            } else if (mouseJustReleased) {
                int currentCol = getMouseColOnBoard();
                int currentRow = getMouseRowOnBoard();

                if (currentCol == activeP.preCol && currentRow == activeP.preRow) {
                    activeP.resetPosition();
                    canMove = false;
                    validSquare = false;
                } else {
                    if (validSquare) {
                        // Finalize and commit a valid move
                        commitMove();
                    } else {
                        // Cancel the current move and restore the board state
                        cancelMove();
                    }
                }
            }
        }
    }

    // Select a piece when the player clicks on it
    private void selectActivePiece() {
        int mouseCol = getMouseColOnBoard();
        int mouseRow = getMouseRowOnBoard();

        if (!isWithinBoard(mouseCol, mouseRow)) {
            return;
        }

        for (Piece p : simPieces) {
            if (p.color == currentColor && p.col == mouseCol && p.row == mouseRow) {
                activeP = p;
                canMove = false;
                validSquare = false;
                // Capture the board state before this piece (or a castling
                // rook) gets mutated by simulate() during the drag
                capturePreMoveSnapshot();
                // Update all legal move highlights for the selected piece
                refreshLegalMoveSquares();
                return;
            }
        }
    }

    // Finalize and commit a valid move
    private void commitMove() {
        // Snap back to live view whenever a new move is made
        viewMoveIndex = -1;

        ArrayList<Piece> savedSimPieces = new ArrayList<>(simPieces);

        boolean isCapture = activeP.hittingP != null;
        boolean isCastling = castlingP != null;
        int fromCol = activeP.preCol;
        int fromRow = activeP.preRow;
        int toCol = activeP.col;
        int toRow = activeP.row;

        String san = generateSAN(activeP, toCol, toRow, isCapture, isCastling);

        // Store move in ORIGINAL coordinates (flip back if board is currently flipped)
        if (boardFlipped) {
            fromCol = 7 - fromCol;
            fromRow = 7 - fromRow;
            toCol = 7 - toCol;
            toRow = 7 - toRow;
        }

        saveHistorySnapshot();
        copyPieces(savedSimPieces, simPieces);

        copyPieces(simPieces, pieces);
        activeP.updatePosition();
        legalMoveSquares.clear();

        if (castlingP != null) {
            castlingP.updatePosition();
        }

        MoveRecord record = new MoveRecord(activeP.type, fromCol, fromRow, toCol, toRow, currentColor, isCapture, isCastling, san);
        moves.add(record);

        if (isCapture && activeP.hittingP != null) {
            capturedPieces.add(activeP.hittingP);
        }

        // Check whether a pawn can be promoted
        if (canPromote()) {
            promotion = true;
        } else {
            // Complete the current turn and check game state
            finishTurn();
        }
    }

    // Cancel the current move and restore the board state
    private void cancelMove() {
        copyPieces(pieces, simPieces);
        activeP.resetPosition();
        activeP = null;
        castlingP = null;
        canMove = false;
        validSquare = false;
        legalMoveSquares.clear();
    }

    // Handle pawn promotion selection
    private void promote() {
        if (mouse.pressed) {
            for (Piece p : promoPieces) {
                if (p.col == mouse.x / Board.SIZE && p.row == mouse.y / Board.SIZE) {
                    PieceType promoType = p.type;
                    String suffix = "";
                    switch (promoType) {
                        case ROOK:
                            simPieces.add(new Rook(activeP.col, activeP.row, currentColor));
                            suffix = "=R";
                            break;
                        case KNIGHT:
                            simPieces.add(new Knight(activeP.col, activeP.row, currentColor));
                            suffix = "=N";
                            break;
                        case BISHOP:
                            simPieces.add(new Bishop(activeP.col, activeP.row, currentColor));
                            suffix = "=B";
                            break;
                        default:
                            simPieces.add(new Queen(activeP.col, activeP.row, currentColor));
                            suffix = "=Q";
                            break;
                    }
                    simPieces.remove(activeP.getIndex());
                    copyPieces(simPieces, pieces);

                    if (!moves.isEmpty()) {
                        MoveRecord lastMove = moves.get(moves.size() - 1);
                        lastMove.san += suffix;
                        lastMove.promotionType = promoType.toString();
                    }

                    activeP = null;
                    promotion = false;
                    canMove = false;
                    validSquare = false;
                    legalMoveSquares.clear();
                    // Complete the current turn and check game state
                    finishTurn();
                    return;
                }
            }
        }
    }

    // Simulate a move before committing it
    private void simulate() {
        canMove = false;
        validSquare = false;

        copyPieces(pieces, simPieces);

        if (castlingP != null) {
            castlingP.col = castlingP.preCol;
            castlingP.x = castlingP.getX(castlingP.col);
            castlingP = null;
        }

        activeP.x = mouse.x - Board.SIZE / 2;
        activeP.y = mouse.y - Board.SIZE / 2;
        activeP.col = activeP.getCol(activeP.x);
        activeP.row = activeP.getRow(activeP.y);

        if (activeP.canMove(activeP.col, activeP.row)) {
            canMove = true;

            if (activeP.hittingP != null) {
                int hitIndex = activeP.hittingP.getIndex();
                if (hitIndex >= 0) {
                    simPieces.remove(hitIndex);
                }
            }

            // Check if castling passes through a square under attack
            boolean castlingThroughCheck = isCastlingThroughCheck(activeP);
            // Move the rook when castling occurs
            checkCastling();

            if (!castlingThroughCheck && !isKingInCheck(currentColor)) {
                validSquare = true;
            }
        }
    }

    // Switch turns between white and black
    private void changePlayer() {
        currentColor = getOppositeColor(currentColor);
        for (Piece p : pieces) {
            if (p.color == currentColor) {
                p.twoStepped = false;
            }
        }
        activeP = null;
    }

    // Complete the current turn and check game state
    private void finishTurn() {
        int opponent = getOppositeColor(currentColor);
        checkingP = findCheckingPiece(opponent);
        boolean opponentHasLegalMove = hasLegalMove(opponent);

        if (!moves.isEmpty()) {
            MoveRecord lastMove = moves.get(moves.size() - 1);
            if (checkingP != null) {
                if (!opponentHasLegalMove) {
                    lastMove.san += "#";
                } else {
                    lastMove.san += "+";
                }
            }
        }

        scrollToBottom();

        if ((checkingP != null && !opponentHasLegalMove) || whiteResign || blackResign) {
            gameOver = true;
            activeP = null;
            legalMoveSquares.clear();
        } else if (checkingP == null && !opponentHasLegalMove) {
            stalemate = true;
            activeP = null;
            legalMoveSquares.clear();
        } else {
            // Switch turns between white and black
            changePlayer();
        }
    }

    public void resign(int color) {
        if (color == 0) {
            whiteResign = true;
        } else {
            blackResign = true;
        }
        gameOver = true;
        activeP = null;
        legalMoveSquares.clear();
    }

    // Move the rook when castling occurs
    private void checkCastling() {
        if (castlingP != null) {
            castlingP.col = (castlingP.col == 0) ? 3 : 5;
            castlingP.x = castlingP.getX(castlingP.col);
        }
    }

    // Check whether a pawn can be promoted
    private boolean canPromote() {
        if (activeP.type == PieceType.PAWN) {
            if ((currentColor == WHITE && activeP.row == 0) || (currentColor == BLACK && activeP.row == 7)) {
                promoPieces.clear();
                promoPieces.add(new Rook(9, 2, currentColor));
                promoPieces.add(new Knight(9, 3, currentColor));
                promoPieces.add(new Bishop(9, 4, currentColor));
                promoPieces.add(new Queen(9, 5, currentColor));
                return true;
            }
        }
        return false;
    }

    // Verify that a square is inside the board boundaries
    public boolean isWithinBoard(int col, int row) {
        return col >= 0 && col < 8 && row >= 0 && row < 8;
    }

    // Find and return the king of the specified color
    private Piece getKing(int color) {
        for (Piece p : simPieces) {
            if (p.type == PieceType.KING && p.color == color) {
                return p;
            }
        }
        return null;
    }

    // Find the piece currently checking a king
    private Piece findCheckingPiece(int kingColor) {
        Piece king = getKing(kingColor);
        if (king == null) return null;

        for (Piece p : simPieces) {
            if (p.color != kingColor && attacksSquare(p, king.col, king.row)) {
                return p;
            }
        }
        return null;
    }

    // Determine whether a king is in check
    private boolean isKingInCheck(int kingColor) {
        return findCheckingPiece(kingColor) != null;
    }

    // Check if a square is attacked by a given color
    private boolean isSquareAttacked(int col, int row, int attackingColor) {
        for (Piece p : simPieces) {
            if (p.color == attackingColor && attacksSquare(p, col, row)) {
                return true;
            }
        }
        return false;
    }

    // Determine whether a piece attacks a specific square
    private boolean attacksSquare(Piece piece, int targetCol, int targetRow) {
        int colDiff = Math.abs(targetCol - piece.col);
        int rowDiff = Math.abs(targetRow - piece.row);

        switch (piece.type) {
            case PAWN:
                int direction = piece.color == WHITE ? -1 : 1;
                if (boardFlipped) {
                    direction = -direction;
                }
                return rowDiff == 1 && targetRow == piece.row + direction && colDiff == 1;
            case KNIGHT:
                return (colDiff == 1 && rowDiff == 2) || (colDiff == 2 && rowDiff == 1);
            case BISHOP:
                return colDiff == rowDiff && isPathClear(piece, targetCol, targetRow);
            case ROOK:
                return (targetCol == piece.col || targetRow == piece.row) && isPathClear(piece, targetCol, targetRow);
            case QUEEN:
                boolean straight = targetCol == piece.col || targetRow == piece.row;
                boolean diagonal = colDiff == rowDiff;
                return (straight || diagonal) && isPathClear(piece, targetCol, targetRow);
            case KING:
                return colDiff <= 1 && rowDiff <= 1;
            default:
                return false;
        }
    }

    // Check if there are pieces blocking a path
    private boolean isPathClear(Piece piece, int targetCol, int targetRow) {
        int colDirection = Integer.compare(targetCol, piece.col);
        int rowDirection = Integer.compare(targetRow, piece.row);
        int currentCol = piece.col + colDirection;
        int currentRow = piece.row + rowDirection;

        while (currentCol != targetCol || currentRow != targetRow) {
            if (getPieceAt(currentCol, currentRow) != null) {
                return false;
            }
            currentCol += colDirection;
            currentRow += rowDirection;
        }

        return true;
    }

    // Get the piece located on a specific square
    public Piece getPieceAt(int col, int row) {
        for (Piece p : simPieces) {
            if (p.col == col && p.row == row) {
                return p;
            }
        }
        return null;
    }


    // Check if castling passes through a square under attack
    private boolean isCastlingThroughCheck(Piece king) {
        if (king == null || king.type != PieceType.KING || Math.abs(king.col - king.preCol) != 2) {
            return false;
        }

        int originalCol = king.col;
        int originalRow = king.row;
        int direction = king.col > king.preCol ? 1 : -1;
        int attackingColor = getOppositeColor(king.color);

        for (int step = 0; step <= 2; step++) {
            int col = king.preCol + direction * step;
            king.col = col;
            king.row = king.preRow;

            if (isSquareAttacked(col, king.preRow, attackingColor)) {
                king.col = originalCol;
                king.row = originalRow;
                return true;
            }
        }

        king.col = originalCol;
        king.row = originalRow;
        return false;
    }

    // Determine whether a player has any legal moves available
    private boolean hasLegalMove(int color) {
        Piece savedCastlingP = castlingP;

        for (Piece piece : new ArrayList<>(pieces)) {
            if (piece.color != color) {
                continue;
            }

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (canLegallyMove(piece, col, row)) {
                        castlingP = savedCastlingP;
                        copyPieces(pieces, simPieces);
                        return true;
                    }
                }
            }
        }

        castlingP = savedCastlingP;
        copyPieces(pieces, simPieces);
        return false;
    }

    // Test whether a move is legal without committing it
    private boolean canLegallyMove(Piece piece, int targetCol, int targetRow) {
        int[] savedState = piece.saveState();
        Piece savedHittingP = piece.hittingP;
        Piece savedCastlingP = castlingP;

        copyPieces(pieces, simPieces);
        castlingP = null;

        piece.col = targetCol;
        piece.row = targetRow;

        boolean legal = false;

        if (piece.canMove(targetCol, targetRow)) {
            if (piece.hittingP != null) {
                int hitIndex = piece.hittingP.getIndex();
                if (hitIndex >= 0) {
                    simPieces.remove(hitIndex);
                }
            }

            // Check if castling passes through a square under attack
            boolean castlingThroughCheck = isCastlingThroughCheck(piece);
            Piece rook = castlingP;
            int savedRookCol = rook != null ? rook.col : 0;
            int savedRookX   = rook != null ? rook.x   : 0;

            // Move the rook when castling occurs
            checkCastling();
            legal = !castlingThroughCheck && !isKingInCheck(piece.color);

            if (rook != null) {
                rook.col = savedRookCol;
                rook.x   = savedRookX;
            }
        }

        piece.restoreState(savedState);
        piece.hittingP = savedHittingP;
        castlingP = savedCastlingP;
        copyPieces(pieces, simPieces);

        return legal;
    }

    // Update all legal move highlights for the selected piece
    private void refreshLegalMoveSquares() {
        legalMoveSquares.clear();

        if (activeP == null) {
            return;
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (canLegallyMove(activeP, col, row)) {
                    legalMoveSquares.add(new Point(col, row));
                }
            }
        }

        copyPieces(pieces, simPieces);
    }

    // Get the opposite player's color
    public int getOppositeColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }

    // Toggle the board flip state and flip all piece coordinates
    public void toggleFlipBoard() {
        boardFlipped = !boardFlipped;
        isBoardFlipped = boardFlipped;
        
        // Flip all piece coordinates in the game
        for (Piece p : simPieces) {
            p.col = 7 - p.col;
            p.row = 7 - p.row;
            p.preCol = 7 - p.preCol;
            p.preRow = 7 - p.preRow;
            p.x = p.getX(p.col);
            p.y = p.getY(p.row);
        }
        
        // Flip legal move squares
        for (Point square : legalMoveSquares) {
            square.x = 7 - square.x;
            square.y = 7 - square.y;
        }
        
        // Note: Move history (moves) is not flipped - it records moves in the original coordinate system
        
        // Flip castling piece if it exists
        if (castlingP != null) {
            castlingP.col = 7 - castlingP.col;
            castlingP.row = 7 - castlingP.row;
        }
    }

    // Get the flipped coordinate for a given board position
    public int getFlippedCol(int col) {
        return 7 - col;
    }

    public int getFlippedRow(int row) {
        return 7 - row;
    }

    public boolean isBoardFlipped() {
        return boardFlipped;
    }

    // Convert mouse coordinates to board coordinates
    private int getMouseColOnBoard() {
        return mouse.x / Board.SIZE;
    }

    private int getMouseRowOnBoard() {
        return mouse.y / Board.SIZE;
    }

    public String generateSAN(Piece piece, int targetCol, int targetRow, boolean isCapture, boolean isCastling) {
        if (isCastling) {
            return targetCol > piece.preCol ? "O-O" : "O-O-O";
        }

        StringBuilder sb = new StringBuilder();

        if (piece.type == PieceType.PAWN) {
            if (isCapture) {
                sb.append((char) ('a' + piece.preCol));
                sb.append('x');
            }
            sb.append((char) ('a' + targetCol));
            sb.append(8 - targetRow);
        } else {
            String prefix = piece.type.getNotation();
            sb.append(prefix);

            boolean shareFile = false;
            boolean shareRank = false;
            boolean anotherCanMove = false;

            for (Piece p : pieces) {
                if (p != piece && p.color == piece.color && p.type == piece.type) {
                    if (canLegallyMove(p, targetCol, targetRow)) {
                        anotherCanMove = true;
                        if (p.col == piece.preCol) {
                            shareFile = true;
                        }
                        if (p.row == piece.preRow) {
                            shareRank = true;
                        }
                    }
                }
            }

            if (anotherCanMove) {
                if (shareFile && shareRank) {
                    sb.append((char) ('a' + piece.preCol));
                    sb.append(8 - piece.preRow);
                } else if (shareFile) {
                    sb.append(8 - piece.preRow);
                } else {
                    sb.append((char) ('a' + piece.preCol));
                }
            }

            if (isCapture) {
                sb.append('x');
            }

            sb.append((char) ('a' + targetCol));
            sb.append(8 - targetRow);
        }

        return sb.toString();
    }

    public void scrollMoveLog(int notches) {
        int totalPairs = (moves.size() + 1) / 2;
        int maxVisible = 10;
        if (totalPairs <= maxVisible) {
            scrollStartLine = 0;
            return;
        }
        scrollStartLine += notches;
        if (scrollStartLine < 0) {
            scrollStartLine = 0;
        }
        if (scrollStartLine > totalPairs - maxVisible) {
            scrollStartLine = totalPairs - maxVisible;
        }
    }

    public void scrollToBottom() {
        int totalPairs = (moves.size() + 1) / 2;
        int maxVisible = 10;
        if (totalPairs > maxVisible) {
            scrollStartLine = totalPairs - maxVisible;
        } else {
            scrollStartLine = 0;
        }
    }

    public ArrayList<Piece> getCapturedPieces(int pieceColor) {
        ArrayList<Piece> list = new ArrayList<>();
        for (Piece p : capturedPieces) {
            if (p.color == pieceColor) {
                list.add(p);
            }
        }
        return list;
    }

    public int getPieceValue(PieceType type) {
        return type.getValue();
    }

    public ArrayList<Piece> getSortedCapturedPieces(int pieceColor) {
        // Invalidate cache if captured pieces changed
        if (cachedCapturedCount != capturedPieces.size()) {
            cachedCapturedCount = capturedPieces.size();
            cachedSortedWhite = null;
            cachedSortedBlack = null;
        }
        
        if (pieceColor == WHITE) {
            if (cachedSortedWhite == null) {
                cachedSortedWhite = getCapturedPieces(WHITE);
                cachedSortedWhite.sort((p1, p2) -> Integer.compare(getPieceValue(p1.type), getPieceValue(p2.type)));
            }
            return cachedSortedWhite;
        } else {
            if (cachedSortedBlack == null) {
                cachedSortedBlack = getCapturedPieces(BLACK);
                cachedSortedBlack.sort((p1, p2) -> Integer.compare(getPieceValue(p1.type), getPieceValue(p2.type)));
            }
            return cachedSortedBlack;
        }
    }

    public int getCapturedValueByWhite() {
        int sum = 0;
        for (Piece p : capturedPieces) {
            if (p.color == BLACK) {
                sum += getPieceValue(p.type);
            }
        }
        return sum;
    }

    public int getCapturedValueByBlack() {
        int sum = 0;
        for (Piece p : capturedPieces) {
            if (p.color == WHITE) {
                sum += getPieceValue(p.type);
            }
        }
        return sum;
    }

    // viewMoveIndex: -1 means live/current view; otherwise number of moves to display (0..moves.size())
    public int viewMoveIndex = -1;

    // Navigation / viewing helpers
    public void viewStart() {
        viewMoveIndex = 0;
    }

    public void viewEnd() {
        viewMoveIndex = -1;
    }

    public void viewNext() {
        if (viewMoveIndex == -1) {
            // Already at the live end, nothing to do
            return;
        }
        if (viewMoveIndex < moves.size()) {
            viewMoveIndex++;
        }
        // If we've reached the end of the move list, snap to live view
        if (viewMoveIndex >= moves.size()) {
            viewMoveIndex = -1;
        }
    }

    public void viewPrevious() {
        if (viewMoveIndex == -1) {
            // Start from the last move and go back one
            viewMoveIndex = moves.size() - 1;
        } else if (viewMoveIndex > 0) {
            viewMoveIndex--;
        }
    }

    public int getViewMoveIndex() {
        return viewMoveIndex;
    }

    // Return the pieces that should be used for rendering based on the current view index
    public ArrayList<Piece> getDisplayPieces() {
        if (viewMoveIndex == -1) {
            return simPieces;
        }
        return getBoardAtMoveCount(viewMoveIndex);
    }

    // Build a fresh board for display after applying `moveCount` moves (0..moves.size())
    private ArrayList<Piece> getBoardAtMoveCount(int moveCount) {
        // Create initial position
        ArrayList<Piece> board = new ArrayList<>();
        for (int col = 0; col < 8; col++) {
            board.add(new Pawn(col, 6, WHITE));
            board.add(new Pawn(col, 1, BLACK));
        }
        int[] backRankCols = {0, 7, 1, 6, 2, 5, 3, 4};
        for (int i = 0; i < backRankCols.length; i++) {
            int col = backRankCols[i];
            Piece whitePiece, blackPiece;
            if      (i < 2) { whitePiece = new Rook(col, 7, WHITE);   blackPiece = new Rook(col, 0, BLACK); }
            else if (i < 4) { whitePiece = new Knight(col, 7, WHITE); blackPiece = new Knight(col, 0, BLACK); }
            else if (i < 6) { whitePiece = new Bishop(col, 7, WHITE); blackPiece = new Bishop(col, 0, BLACK); }
            else if (i < 7) { whitePiece = new Queen(col, 7, WHITE);  blackPiece = new Queen(col, 0, BLACK); }
            else            { whitePiece = new King(col, 7, WHITE);   blackPiece = new King(col, 0, BLACK); }
            board.add(whitePiece);
            board.add(blackPiece);
        }

        // If board is currently flipped, flip all starting positions
        if (boardFlipped) {
            for (Piece p : board) {
                p.col = 7 - p.col;
                p.row = 7 - p.row;
                p.preCol = 7 - p.preCol;
                p.preRow = 7 - p.preRow;
                p.x = p.getX(p.col);
                p.y = p.getY(p.row);
            }
        }

        // Apply moves sequentially onto the board copy
        int applyCount = Math.max(0, Math.min(moveCount, moves.size()));
        for (int i = 0; i < applyCount; i++) {
            applyMoveToBoard(board, moves.get(i));
        }

        return board;
    }

    // Apply a single MoveRecord to a mutable board list (modifies the list)
    private void applyMoveToBoard(ArrayList<Piece> board, MoveRecord mr) {
        // Move history is always stored in original (unflipped) coordinates,
        // so we need to convert to display coordinates if the board is flipped
        int fromCol = boardFlipped ? 7 - mr.fromCol : mr.fromCol;
        int fromRow = boardFlipped ? 7 - mr.fromRow : mr.fromRow;
        int toCol   = boardFlipped ? 7 - mr.toCol   : mr.toCol;
        int toRow   = boardFlipped ? 7 - mr.toRow   : mr.toRow;

        // Find moving piece by from coordinates and color (prefer matching type)
        Piece mover = null;
        for (Piece p : board) {
            if (p.col == fromCol && p.row == fromRow && p.color == mr.color) {
                if (p.type == mr.type) {
                    mover = p;
                    break;
                }
                if (mover == null) mover = p;
            }
        }
        if (mover == null) {
            return; // can't find piece, give up
        }

        // Handle capture: remove piece at destination or en-passant style
        if (mr.isCapture) {
            Piece captured = null;
            for (Piece p : board) {
                if (p.col == toCol && p.row == toRow && p.color != mr.color) {
                    captured = p;
                    break;
                }
            }
            if (captured == null) {
                // try en-passant style capture: piece on target column but at mover's fromRow
                for (Piece p : board) {
                    if (p.col == toCol && p.row == fromRow && p.color != mr.color && p.type == PieceType.PAWN) {
                        captured = p;
                        break;
                    }
                }
            }
            if (captured != null) {
                board.remove(captured);
            }
        }

        // Handle castling: move rook accordingly
        if (mr.isCastling && mr.type == PieceType.KING) {
            int rookFromCol = boardFlipped ? 7 - (mr.toCol > mr.fromCol ? 7 : 0) : (mr.toCol > mr.fromCol ? 7 : 0);
            int rookToCol   = boardFlipped ? 7 - (mr.toCol > mr.fromCol ? mr.toCol - 1 : mr.toCol + 1)
                                           : (mr.toCol > mr.fromCol ? mr.toCol - 1 : mr.toCol + 1);
            for (Piece p : board) {
                if (p.col == rookFromCol && p.row == fromRow && p.color == mr.color && p.type == PieceType.ROOK) {
                    p.col = rookToCol;
                    p.row = toRow;
                    p.x = p.getX(p.col);
                    p.y = p.getY(p.row);
                    break;
                }
            }
        }

        // Move the piece
        mover.col = toCol;
        mover.row = toRow;
        mover.x = mover.getX(mover.col);
        mover.y = mover.getY(mover.row);
        mover.moved = true;

        // Handle promotion
        if (mr.promotionType != null) {
            // replace pawn with promoted piece
            Piece promoted = null;
            switch (mr.promotionType) {
                case "ROOK":   promoted = new Rook(mover.col, mover.row, mover.color);   break;
                case "KNIGHT": promoted = new Knight(mover.col, mover.row, mover.color); break;
                case "BISHOP": promoted = new Bishop(mover.col, mover.row, mover.color); break;
                default:       promoted = new Queen(mover.col, mover.row, mover.color);  break;
            }
            board.remove(mover);
            board.add(promoted);
        }
    }
}