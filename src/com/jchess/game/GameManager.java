package com.jchess.game;

import com.jchess.audio.SoundManager;
import com.jchess.input.Mouse;
import com.jchess.model.Board;
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
    public int playerColor = WHITE;

    // Declare all the variables
    public ArrayList<Piece> pieces = new ArrayList<>();
    public static ArrayList<Piece> simPieces = new ArrayList<>();
    public ArrayList<Piece> promoPieces = new ArrayList<>();
    public ArrayList<Point> legalMoveSquares = new ArrayList<>();
    public ArrayList<MoveRecord> moves = new ArrayList<>();
    public ArrayList<Piece> capturedPieces = new ArrayList<>();
    public int scrollStartLine = 0;

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
    public boolean isInsufficientMaterial = false;
    public Integer timeOutWinner = null; // null if not time-out, 0 for White, 1 for Black

    private final Mouse mouse;
    
    // Animation tracking
    private final ArrayList<PieceAnimation> animations = new ArrayList<>();
    
    // Caches for optimization
    private ArrayList<Piece> cachedSortedWhite;
    private ArrayList<Piece> cachedSortedBlack;
    private int cachedCapturedCount = -1;

    // Per-move time tracking
    public long moveStartTimeMillis = 0; // System.currentTimeMillis() when current player's turn started

    private final MoveValidator moveValidator;
    private final GameHistoryManager historyManager;

    public GameManager() {
        this.mouse = new Mouse();
        this.moveValidator = new MoveValidator(this);
        this.historyManager = new GameHistoryManager(this);
        setPieces();
        copyPieces(pieces, simPieces);
    }

    public GameManager(Mouse mouse) {
        this.mouse = mouse;
        this.moveValidator = new MoveValidator(this);
        this.historyManager = new GameHistoryManager(this);
        setPieces();
        copyPieces(pieces, simPieces);
    }

    public MoveValidator getMoveValidator() {
        return moveValidator;
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

    public void resetGameState() {
        clearAnimations();
        historyManager.resetHistory();

        setPieces();
        copyPieces(pieces, simPieces);
        promoPieces.clear();
        legalMoveSquares.clear();

        activeP = null;
        checkingP = null;
        currentColor = WHITE;
        canMove = false;
        validSquare = false;
        promotion = false;
        gameOver = false;
        stalemate = false;
        boardFlipped = false;
        isBoardFlipped = false;
        whiteResign = false;
        blackResign = false;
        timeOutWinner = null;
        castlingP = null;
        resetCapturedPieceCache();
        scrollStartLine = 0;
        moveStartTimeMillis = System.currentTimeMillis();
    }

    public void copyPieces(ArrayList<Piece> src, ArrayList<Piece> tgt) {
        tgt.clear();
        tgt.addAll(src);
    }

    public boolean canUndo() {
        return historyManager.canUndo();
    }

    public void undoLastMove() {
        historyManager.undoLastMove();
    }

    private void updateAnimations() {
        ArrayList<PieceAnimation> completedAnimations = new ArrayList<>();
        
        for (PieceAnimation anim : animations) {
            boolean isComplete = anim.update();
            if (isComplete) {
                anim.piece.col = anim.endCol;
                anim.piece.row = anim.endRow;
                anim.piece.updatePosition();
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
                    Piece clickedPiece = moveValidator.getPieceAt(mouseCol, mouseRow);

                    if (clickedPiece == activeP) {
                        cancelMove();
                    } else if (clickedPiece != null && clickedPiece.color == currentColor) {
                        activeP.resetPosition();
                        activeP = clickedPiece;
                        canMove = false;
                        validSquare = false;
                        historyManager.capturePreMoveSnapshot();
                        moveValidator.refreshLegalMoveSquares();
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

        if (!moveValidator.isWithinBoard(mouseCol, mouseRow)) {
            return;
        }

        for (Piece p : simPieces) {
            if (p.color == currentColor && p.col == mouseCol && p.row == mouseRow) {
                activeP = p;
                canMove = false;
                validSquare = false;
                historyManager.capturePreMoveSnapshot();
                moveValidator.refreshLegalMoveSquares();
                return;
            }
        }
    }

    private void commitMove() {
        historyManager.viewEnd();

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

        historyManager.saveHistorySnapshot();
        copyPieces(savedSimPieces, simPieces);

        copyPieces(simPieces, pieces);
        
        animations.add(new PieceAnimation(activeP, activeP.preCol, activeP.preRow, activeP.col, activeP.row));
        
        if (castlingP != null) {
            animations.add(new PieceAnimation(castlingP, castlingP.preCol, castlingP.preRow, castlingP.col, castlingP.row));
            castlingP = null;
        }
        
        legalMoveSquares.clear();

        MoveRecord record = new MoveRecord(activeP.type, fromCol, fromRow, toCol, toRow, currentColor, isCapture, isCastling, san);
        // Record time spent on this move
        if (moveStartTimeMillis > 0) {
            record.timeSpentSeconds = (int) ((System.currentTimeMillis() - moveStartTimeMillis) / 1000);
        }
        moveStartTimeMillis = System.currentTimeMillis();
        moves.add(record);

        if (isCapture && activeP.hittingP != null) {
            capturedPieces.add(activeP.hittingP);
            SoundManager.playCapture();
        } else {
            SoundManager.playMove();
        }

        if (moveValidator.canPromote()) {
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

            boolean castlingThroughCheck = moveValidator.isCastlingThroughCheck(activeP);
            moveValidator.checkCastling(activeP);

            if (!castlingThroughCheck && !moveValidator.isKingInCheck(currentColor)) {
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
        checkingP = moveValidator.findCheckingPiece(opponent);
        boolean opponentHasLegalMove = moveValidator.hasLegalMove(opponent);

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
            SoundManager.playBeep();
        } else if (checkingP == null && !opponentHasLegalMove) {
            stalemate = true;
            activeP = null;
            legalMoveSquares.clear();
            SoundManager.playBeep();
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

    public void timeOutWin(int winnerColor) {
        gameOver = true;
        activeP = null;
        legalMoveSquares.clear();
        timeOutWinner = winnerColor;
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
        castlingP = null;
    }

    public boolean isBoardFlipped() {
        return boardFlipped;
    }

    void clearAnimations() {
        animations.clear();
    }

    void resetCapturedPieceCache() {
        cachedSortedWhite = null;
        cachedSortedBlack = null;
        cachedCapturedCount = capturedPieces.size();
    }
    
    public int getPlayerColor() {
        return playerColor;
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
                    if (moveValidator.canLegallyMove(p, targetCol, targetRow)) {
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

    public String getFEN() {
        StringBuilder fen = new StringBuilder();
        // Build piece placement (ranks 8 to 1, row 0 = rank 1, row 7 = rank 8)
        // FEN displays from White's perspective: rank 8 (black side) first, then down to rank 1 (white side)
        for (int rank = 8; rank >= 1; rank--) {
            int row = rank - 1; // Convert rank to internal row
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = getPieceAt(col, row);
                if (piece != null) {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    char pieceChar = getPieceChar(piece);
                    fen.append(pieceChar);
                } else {
                    emptyCount++;
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank > 1) {
                fen.append('/');
            }
        }

        

        // Active color
        fen.append(' ').append(currentColor == WHITE ? 'w' : 'b');

        // Castling rights
        StringBuilder castling = new StringBuilder();
        if (hasCastlingRights(WHITE, true))  castling.append('K');
        if (hasCastlingRights(WHITE, false)) castling.append('Q');
        if (hasCastlingRights(BLACK, true))  castling.append('k');
        if (hasCastlingRights(BLACK, false)) castling.append('q');
        if (castling.length() == 0) castling.append('-');
        fen.append(' ').append(castling);

        // En passant target
        String enPassant = getEnPassantSquare();
        fen.append(' ').append(enPassant);

        // Halfmove clock
        int halfmove = getHalfmoveClock();
        fen.append(' ').append(halfmove);

        // Fullmove number
        int fullmove = moves.size() / 2 + 1;
        fen.append(' ').append(fullmove);

        return fen.toString();
    }

    private Piece getPieceAt(int col, int row) {
        for (Piece p : pieces) {
            if (p.col == col && p.row == row) {
                return p;
            }
        }
        return null;
    }

    private char getPieceChar(Piece piece) {
        char c;
        switch (piece.type) {
            case KING:   c = 'K'; break;
            case QUEEN:  c = 'Q'; break;
            case ROOK:   c = 'R'; break;
            case BISHOP: c = 'B'; break;
            case KNIGHT: c = 'N'; break;
            case PAWN:   c = 'P'; break;
            default:     return '?';
        }
        return piece.color == BLACK ? Character.toLowerCase(c) : c;
    }

    private boolean hasCastlingRights(int color, boolean kingSide) {
        // Find king
        Piece king = null;
        for (Piece p : pieces) {
            if (p.type == PieceType.KING && p.color == color) {
                king = p;
                break;
            }
        }
        if (king == null || king.moved) return false;

        // Find relevant rook
        int rookCol = kingSide ? 7 : 0;
        for (Piece p : pieces) {
            if (p.type == PieceType.ROOK && p.color == color && p.col == rookCol) {
                return !p.moved;
            }
        }
        return false;
    }

    private String getEnPassantSquare() {
        if (moves.isEmpty()) {
            return "-";
        }
        MoveRecord lastMove = moves.get(moves.size() - 1);
        if (lastMove.type != PieceType.PAWN) {
            return "-";
        }
        int fromRow = lastMove.fromRow;
        int toRow   = lastMove.toRow;
        if (Math.abs(toRow - fromRow) != 2) {
            return "-";
        }
        int epRow = (fromRow + toRow) / 2;
        char epFile = (char) ('a' + lastMove.toCol);
        return String.format("%c%d", epFile, 8 - epRow);
    }

    private int getHalfmoveClock() {
        int count = 0;
        for (int i = moves.size() - 1; i >= 0; i--) {
            MoveRecord m = moves.get(i);
            if (m.isCapture || PieceType.PAWN.equals(m.type)) {
                break;
            }
            count++;
        }
        return count;
    }

    public void viewStart() {
        historyManager.viewStart();
    }

    public void viewEnd() {
        historyManager.viewEnd();
    }

    public void viewNext() {
        historyManager.viewNext();
    }

    public void viewPrevious() {
        historyManager.viewPrevious();
    }

    public int getViewMoveIndex() {
        return historyManager.getViewMoveIndex();
    }

    public ArrayList<Piece> getDisplayPieces() {
        return historyManager.getDisplayPieces();
    }
}