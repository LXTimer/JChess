package com.jchess.game;

import com.jchess.input.Mouse;
import com.jchess.model.Board;
import com.jchess.model.BoardState;
import com.jchess.model.Piece;
import com.jchess.model.piece.*;
import com.jchess.view.animation.PieceAnimation;
import com.jchess.util.MoveRecord;
import java.awt.Point;
import java.util.ArrayList;
import java.awt.event.*;

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
    private ArrayList<Piece> preMoveSnapshot;

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
    
    // Animation tracking
    private final ArrayList<PieceAnimation> animations = new ArrayList<>();
    
    // Caches for optimization
    private ArrayList<Piece> cachedSortedWhite;
    private ArrayList<Piece> cachedSortedBlack;
    private int cachedCapturedCount = -1;

    public GameManager() {
        this.mouse = new Mouse();
        setPieces();
        copyPieces(pieces, simPieces);
    }

    public GameManager(Mouse mouse) {
        this.mouse = mouse;
        setPieces();
        copyPieces(pieces, simPieces);
    }

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

        animations.clear();

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

        viewMoveIndex = -1;

        if (!gameOver && !stalemate) {
            checkingP = findCheckingPiece(getOppositeColor(currentColor));
        }

        scrollToBottom();
    }

    private void updateAnimations() {
        ArrayList<PieceAnimation> completedAnimations = new ArrayList<>();
        
        for (PieceAnimation anim : animations) {
            boolean isComplete = anim.update();
            if (isComplete) {
                anim.piece.col = anim.endCol;
                anim.piece.row = anim.endRow;
                anim.piece.updatePixelPosition();
                completedAnimations.add(anim);
            } else {
                anim.piece.x = anim.getAnimatedX();
                anim.piece.y = anim.getAnimatedY();
            }
        }
        
        animations.removeAll(completedAnimations);
    }

    public boolean hasAnimations() {
        return !animations.isEmpty();
    }

    public void update(boolean mouseJustPressed, boolean mouseJustReleased) {
        updateAnimations();
        
        if (promotion) {
            promote();
            return;
        }

        if (gameOver || stalemate) {
            return;
        }

        if (activeP == null) {
            if (mouseJustPressed) {
                selectActivePiece();
            }
        } else {
            if (mouse.pressed) {
                if (mouseJustPressed) {
                    int mouseCol = getMouseColOnBoard();
                    int mouseRow = getMouseRowOnBoard();
                    Piece clickedPiece = getPieceAt(mouseCol, mouseRow);

                    if (clickedPiece == activeP) {
                        cancelMove();
                    } else if (clickedPiece != null && clickedPiece.color == currentColor) {
                        activeP.resetPosition();
                        activeP = clickedPiece;
                        canMove = false;
                        validSquare = false;
                        capturePreMoveSnapshot();
                        refreshLegalMoveSquares();
                    } else {
                        simulate();
                    }
                } else {
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
                        commitMove();
                    } else {
                        cancelMove();
                    }
                }
            }
        }
    }

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
                capturePreMoveSnapshot();
                refreshLegalMoveSquares();
                return;
            }
        }
    }

    private void commitMove() {
        viewMoveIndex = -1;

        ArrayList<Piece> savedSimPieces = new ArrayList<>(simPieces);

        boolean isCapture = activeP.hittingP != null;
        boolean isCastling = castlingP != null;
        int fromCol = activeP.preCol;
        int fromRow = activeP.preRow;
        int toCol = activeP.col;
        int toRow = activeP.row;

        String san = generateSAN(activeP, toCol, toRow, isCapture, isCastling);

        if (boardFlipped) {
            fromCol = 7 - fromCol;
            fromRow = 7 - fromRow;
            toCol = 7 - toCol;
            toRow = 7 - toRow;
        }

        saveHistorySnapshot();
        copyPieces(savedSimPieces, simPieces);

        copyPieces(simPieces, pieces);
        
        animations.add(new PieceAnimation(activeP, activeP.preCol, activeP.preRow, activeP.col, activeP.row));
        
        if (castlingP != null) {
            animations.add(new PieceAnimation(castlingP, castlingP.preCol, castlingP.preRow, castlingP.col, castlingP.row));
        }
        
        legalMoveSquares.clear();

        MoveRecord record = new MoveRecord(activeP.type, fromCol, fromRow, toCol, toRow, currentColor, isCapture, isCastling, san);
        moves.add(record);

        if (isCapture && activeP.hittingP != null) {
            capturedPieces.add(activeP.hittingP);
        }

        if (canPromote()) {
            promotion = true;
        } else {
            finishTurn();
        }
    }

    private void cancelMove() {
        copyPieces(pieces, simPieces);
        activeP.resetPosition();
        activeP = null;
        castlingP = null;
        canMove = false;
        validSquare = false;
        legalMoveSquares.clear();
    }

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
                    finishTurn();
                    return;
                }
            }
        }
    }

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

            boolean castlingThroughCheck = isCastlingThroughCheck(activeP);
            checkCastling();

            if (!castlingThroughCheck && !isKingInCheck(currentColor)) {
                validSquare = true;
            }
        }
    }

    private void changePlayer() {
        currentColor = getOppositeColor(currentColor);
        for (Piece p : pieces) {
            if (p.color == currentColor) {
                p.twoStepped = false;
            }
        }
        activeP = null;
    }

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

    private void checkCastling() {
        if (castlingP != null) {
            castlingP.col = (castlingP.col == 0) ? 3 : 5;
            castlingP.x = castlingP.getX(castlingP.col);
        }
    }

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

    public boolean isWithinBoard(int col, int row) {
        return col >= 0 && col < 8 && row >= 0 && row < 8;
    }

    private Piece getKing(int color) {
        for (Piece p : simPieces) {
            if (p.type == PieceType.KING && p.color == color) {
                return p;
            }
        }
        return null;
    }

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

    private boolean isKingInCheck(int kingColor) {
        return findCheckingPiece(kingColor) != null;
    }

    private boolean isSquareAttacked(int col, int row, int attackingColor) {
        for (Piece p : simPieces) {
            if (p.color == attackingColor && attacksSquare(p, col, row)) {
                return true;
            }
        }
        return false;
    }

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

    public Piece getPieceAt(int col, int row) {
        for (Piece p : simPieces) {
            if (p.col == col && p.row == row) {
                return p;
            }
        }
        return null;
    }

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

            boolean castlingThroughCheck = isCastlingThroughCheck(piece);
            Piece rook = castlingP;
            int savedRookCol = rook != null ? rook.col : 0;
            int savedRookX   = rook != null ? rook.x   : 0;

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

    public int getOppositeColor(int color) {
        return color == WHITE ? BLACK : WHITE;
    }

    public void toggleFlipBoard() {
        boardFlipped = !boardFlipped;
        isBoardFlipped = boardFlipped;
        
        for (Piece p : simPieces) {
            p.col = 7 - p.col;
            p.row = 7 - p.row;
            p.preCol = 7 - p.preCol;
            p.preRow = 7 - p.preRow;
            p.x = p.getX(p.col);
            p.y = p.getY(p.row);
        }
        
        for (Point square : legalMoveSquares) {
            square.x = 7 - square.x;
            square.y = 7 - square.y;
        }
        
        if (castlingP != null) {
            castlingP.col = 7 - castlingP.col;
            castlingP.row = 7 - castlingP.row;
        }
    }

    public int getFlippedCol(int col) {
        return 7 - col;
    }

    public int getFlippedRow(int row) {
        return 7 - row;
    }

    public boolean isBoardFlipped() {
        return boardFlipped;
    }

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

    public int viewMoveIndex = -1;

    public void viewStart() {
        viewMoveIndex = 0;
    }

    public void viewEnd() {
        viewMoveIndex = -1;
    }

    public void viewNext() {
        if (viewMoveIndex == -1) {
            return;
        }
        if (viewMoveIndex < moves.size()) {
            viewMoveIndex++;
        }
        if (viewMoveIndex >= moves.size()) {
            viewMoveIndex = -1;
        }
    }

    public void viewPrevious() {
        if (viewMoveIndex == -1) {
            viewMoveIndex = moves.size() - 1;
        } else if (viewMoveIndex > 0) {
            viewMoveIndex--;
        }
    }

    public int getViewMoveIndex() {
        return viewMoveIndex;
    }

    public ArrayList<Piece> getDisplayPieces() {
        if (viewMoveIndex == -1) {
            return simPieces;
        }
        return getBoardAtMoveCount(viewMoveIndex);
    }

    private ArrayList<Piece> getBoardAtMoveCount(int moveCount) {
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

        int applyCount = Math.max(0, Math.min(moveCount, moves.size()));
        for (int i = 0; i < applyCount; i++) {
            applyMoveToBoard(board, moves.get(i));
        }

        return board;
    }

    private void applyMoveToBoard(ArrayList<Piece> board, MoveRecord mr) {
        int fromCol = boardFlipped ? 7 - mr.fromCol : mr.fromCol;
        int fromRow = boardFlipped ? 7 - mr.fromRow : mr.fromRow;
        int toCol   = boardFlipped ? 7 - mr.toCol   : mr.toCol;
        int toRow   = boardFlipped ? 7 - mr.toRow   : mr.toRow;

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
            return;
        }

        if (mr.isCapture) {
            Piece captured = null;
            for (Piece p : board) {
                if (p.col == toCol && p.row == toRow && p.color != mr.color) {
                    captured = p;
                    break;
                }
            }
            if (captured == null) {
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

        if (mr.isCastling && mr.type == PieceType.KING) {
            int rookFromCol = boardFlipped ? 7 - (mr.toCol > mr.fromCol ? 7 : 0) : (mr.toCol > mr.fromCol ? 7 : 0);
            int rookToCol   = boardFlipped ? 7 - (mr.toCol > mr.fromCol ? mr.toCol - 1 : mr.toCol + 1): (mr.toCol > mr.fromCol ? mr.toCol - 1 : mr.toCol + 1);
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

        mover.col = toCol;
        mover.row = toRow;
        mover.x = mover.getX(mover.col);
        mover.y = mover.getY(mover.row);
        mover.moved = true;

        if (mr.promotionType != null) {
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
