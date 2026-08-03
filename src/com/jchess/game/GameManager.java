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
import java.util.HashMap;
import java.awt.event.*;

public class GameManager {

    public static final int WHITE = 0;
    public static final int BLACK = 1;
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
    public boolean suppressGameOver = false; // When true, game never ends (analysis mode)
    public boolean boardFlipped = false;
    public boolean whiteResign = false;
    public boolean blackResign = false;
    public boolean isInsufficientMaterial = false;
    public Integer timeOutWinner = null; // null if not time-out, 0 for White, 1 for Black

    // Draw detection
    public boolean drawByRepetition = false;
    public boolean drawByFiftyMove = false;
    private HashMap<String, Integer> positionHistory = new HashMap<>();
    // Parallel list of position keys in the order they were recorded (one per move + initial).
    // Kept in sync with positionHistory so undo can decrement the correct counter.
    private final ArrayList<String> positionKeys = new ArrayList<>();

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

    public GameHistoryManager getHistoryManager() {
        return historyManager;
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
        whiteResign = false;
        blackResign = false;
        timeOutWinner = null;
        castlingP = null;
        resetCapturedPieceCache();
        scrollStartLine = 0;
        moveStartTimeMillis = System.currentTimeMillis();
        
        // Reset draw detection
        drawByRepetition = false;
        drawByFiftyMove = false;
        positionHistory.clear();
        positionKeys.clear();
        // Record the initial position
        recordCurrentPosition();
    }

    public void refreshPieceImages() {
        refreshPieceImages(pieces);
        refreshPieceImages(simPieces);
        refreshPieceImages(promoPieces);
        refreshPieceImages(capturedPieces);
        historyManager.refreshStoredPieceImages();
    }

    private void refreshPieceImages(ArrayList<Piece> pieceList) {
        for (Piece piece : pieceList) {
            piece.refreshImage();
        }
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
            createPromotionOptions();
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
                if (p.col == (mouse.x - Board.ORIGIN_X) / Board.SIZE && p.row == (mouse.y - Board.ORIGIN_Y) / Board.SIZE) {
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
                    promoPieces.clear();
                    canMove = false;
                    validSquare = false;
                    legalMoveSquares.clear();
                    finishTurn();
                    return;
                }
            }
        }
    }

    private void createPromotionOptions() {
        promoPieces.clear();
        int rowStep = activeP.row == 0 ? 1 : -1;

        PieceType[] promotionTypes = {PieceType.QUEEN, PieceType.KNIGHT,
                PieceType.ROOK, PieceType.BISHOP};
        for (int i = 0; i < promotionTypes.length; i++) {
            int row = activeP.row + rowStep * i;
            switch (promotionTypes[i]) {
                case KNIGHT:
                    promoPieces.add(new Knight(activeP.col, row, currentColor));
                    break;
                case ROOK:
                    promoPieces.add(new Rook(activeP.col, row, currentColor));
                    break;
                case BISHOP:
                    promoPieces.add(new Bishop(activeP.col, row, currentColor));
                    break;
                default:
                    promoPieces.add(new Queen(activeP.col, row, currentColor));
                    break;
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

    /**
     * Generates a compact key representing the current board position.
     * Used for threefold repetition detection.
     * Includes: piece placement, side to move, castling rights, en passant square.
     */
    private String getPositionKey() {
        StringBuilder sb = new StringBuilder();
        
        // Piece placement (ranks 8 to 1)
        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = getPieceAt(col, row);
                if (piece != null) {
                    if (emptyCount > 0) {
                        sb.append(emptyCount);
                        emptyCount = 0;
                    }
                    char c;
                    switch (piece.type) {
                        case KING:   c = 'K'; break;
                        case QUEEN:  c = 'Q'; break;
                        case ROOK:   c = 'R'; break;
                        case BISHOP: c = 'B'; break;
                        case KNIGHT: c = 'N'; break;
                        default:     c = 'P'; break;
                    }
                    if (piece.color == BLACK) {
                        c = Character.toLowerCase(c);
                    }
                    sb.append(c);
                } else {
                    emptyCount++;
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount);
            }
            if (row < 7) {
                sb.append('/');
            }
        }
        
        // Side to move
        sb.append(' ').append(currentColor == WHITE ? 'w' : 'b');
        
        // Castling rights
        StringBuilder castling = new StringBuilder();
        if (hasCastlingRights(WHITE, true))  castling.append('K');
        if (hasCastlingRights(WHITE, false)) castling.append('Q');
        if (hasCastlingRights(BLACK, true))  castling.append('k');
        if (hasCastlingRights(BLACK, false)) castling.append('q');
        if (castling.length() == 0) castling.append('-');
        sb.append(' ').append(castling);
        
        // En passant target square
        sb.append(' ').append(getEnPassantSquare(moves.size()));
        
        return sb.toString();
    }

    /**
     * Records the current position in the position history for repetition detection.
     */
    private void recordCurrentPosition() {
        String key = getPositionKey();
        positionKeys.add(key);
        positionHistory.put(key, positionHistory.getOrDefault(key, 0) + 1);
    }

    /**
     * Returns a defensive copy of the sequential list of recorded position keys.
     * Used by {@link GameHistoryManager} to snapshot/restore repetition state on undo.
     */
    java.util.List<String> getPositionKeysSnapshot() {
        return new java.util.ArrayList<>(positionKeys);
    }

    /**
     * Restores the repetition detection state from a previously saved snapshot.
     * Rebuilds both the key list and the occurrence counter map.
     *
     * @param keys the recorded position keys, in chronological order
     */
    void restorePositionKeys(java.util.List<String> keys) {
        positionKeys.clear();
        positionHistory.clear();
        if (keys != null) {
            positionKeys.addAll(keys);
            for (String key : keys) {
                positionHistory.put(key, positionHistory.getOrDefault(key, 0) + 1);
            }
        }
    }

    /**
     * Checks if the current position has occurred three times (threefold repetition).
     */
    private boolean isThreefoldRepetition() {
        String key = getPositionKey();
        int count = positionHistory.getOrDefault(key, 0);
        return count >= 3;
    }

    /**
     * Checks if the 50-move rule applies (100 half-moves without pawn move or capture).
     */
    private boolean isFiftyMoveRule() {
        return getHalfmoveClock(moves.size()) >= 100;
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
            if (!suppressGameOver) {
                gameOver = true;
                activeP = null;
                legalMoveSquares.clear();
                SoundManager.playBeep();
            } else {
                // In analysis mode, continue playing even after checkmate
                changePlayer();
                recordCurrentPosition();
            }
        } else if (checkingP == null && !opponentHasLegalMove) {
            if (!suppressGameOver) {
                stalemate = true;
                activeP = null;
                legalMoveSquares.clear();
                SoundManager.playBeep();
            } else {
                // In analysis mode, continue playing even after stalemate
                changePlayer();
                recordCurrentPosition();
            }
        } else {
            changePlayer();
            
            // Record the position after the move is complete and player has switched
            recordCurrentPosition();
            
            // Check for draw conditions
            if (isThreefoldRepetition()) {
                drawByRepetition = true;
                if (!suppressGameOver) {
                    stalemate = true;
                    activeP = null;
                    legalMoveSquares.clear();
                    SoundManager.playBeep();
                }
            } else if (isFiftyMoveRule()) {
                drawByFiftyMove = true;
                if (!suppressGameOver) {
                    stalemate = true;
                    activeP = null;
                    legalMoveSquares.clear();
                    SoundManager.playBeep();
                }
            }
        }
    }

    public void resign(int color) {
        if (suppressGameOver) {
            return; // No resigning in analysis mode
        }
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
        if (suppressGameOver) {
            return; // No time-out in analysis mode
        }
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

        for (Piece p : simPieces) {
            p.col = 7 - p.col;
            p.row = 7 - p.row;
            p.preCol = 7 - p.preCol;
            p.preRow = 7 - p.preRow;
            p.x = p.getX(p.col);
            p.y = p.getY(p.row);
            p.boardFlipped = boardFlipped;
        }

        for (Piece p : promoPieces) {
            p.col = 7 - p.col;
            p.row = 7 - p.row;
            p.preCol = 7 - p.preCol;
            p.preRow = 7 - p.preRow;
            p.x = p.getX(p.col);
            p.y = p.getY(p.row);
        }

        for (Piece p : pieces) {
            p.boardFlipped = boardFlipped;
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
        return (mouse.x - Board.ORIGIN_X) / Board.SIZE;
    }

    private int getMouseRowOnBoard() {
        return (mouse.y - Board.ORIGIN_Y) / Board.SIZE;
    }

    public String generateSAN(Piece piece, int targetCol, int targetRow, boolean isCapture, boolean isCastling) {
        // All coordinates passed here are in display space (they may be flipped
        // when the player is Black). SAN notation must always use absolute
        // (unflipped) coordinates, so convert before formatting.
        int absPreCol = boardFlipped ? 7 - piece.preCol : piece.preCol;
        int absPreRow = boardFlipped ? 7 - piece.preRow : piece.preRow;
        int absTargetCol = boardFlipped ? 7 - targetCol : targetCol;
        int absTargetRow = boardFlipped ? 7 - targetRow : targetRow;

        if (isCastling) {
            // Kingside castling moves the king toward the h-file (increasing column
            // in the unflipped orientation). When the board is flipped, the column
            // direction is inverted, so the comparison must be reversed accordingly.
            boolean kingSide = boardFlipped ? targetCol < piece.preCol : targetCol > piece.preCol;
            return kingSide ? "O-O" : "O-O-O";
        }

        StringBuilder sb = new StringBuilder();

        if (piece.type == PieceType.PAWN) {
            if (isCapture) {
                sb.append((char) ('a' + absPreCol));
                sb.append('x');
            }
            sb.append((char) ('a' + absTargetCol));
            sb.append(8 - absTargetRow);
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
                        // Comparing display-space columns/rows is equivalent to comparing
                        // absolute ones (the 7-x flip preserves file/rank equality).
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
                    sb.append((char) ('a' + absPreCol));
                    sb.append(8 - absPreRow);
                } else if (shareFile) {
                    sb.append(8 - absPreRow);
                } else {
                    sb.append((char) ('a' + absPreCol));
                }
            }

            if (isCapture) {
                sb.append('x');
            }

            sb.append((char) ('a' + absTargetCol));
            sb.append(8 - absTargetRow);
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

    /**
     * Programmatically applies a move from the chess engine to the board.
     * Parameters fromCol/fromRow/toCol/toRow are in display coordinates
     * (i.e. already flipped if boardFlipped is true).
     */
    public void makeEngineMove(int fromCol, int fromRow, int toCol, int toRow, PieceType promoType) {
        // 1. Ensure we are at the live position
        historyManager.viewEnd();

        // 2. Capture pre-move snapshot for undo history (before any mutation)
        historyManager.capturePreMoveSnapshot();

        // 3. Find the moving piece from the live pieces list
        activeP = getPieceAt(fromCol, fromRow);
        if (activeP == null) {
            return;
        }

        // 4. Move the piece in simPieces so canMove validation works
        copyPieces(pieces, simPieces);

        // Store old preCol/Row since activeP is from pieces
        activeP.col = toCol;
        activeP.row = toRow;

        // 5. Detect capture — find enemy piece at target BEFORE position update
        //    We re-search simPieces for a different piece at destination.
        Piece captured = null;
        for (Piece p : simPieces) {
            if (p != activeP && p.col == toCol && p.row == toRow && p.color != activeP.color) {
                captured = p;
                break;
            }
        }

        // 6. Handle en passant capture — detect if pawn moved diagonally onto empty square
        if (captured == null && activeP.type == PieceType.PAWN && fromCol != toCol) {
            // En passant: captured pawn is on the same row as the attacker's start row
            for (Piece p : simPieces) {
                if (p != activeP && p.col == toCol && p.row == fromRow
                        && p.color != activeP.color && p.type == PieceType.PAWN) {
                    captured = p;
                    break;
                }
            }
        }

        if (captured != null) {
            simPieces.remove(captured);
            capturedPieces.add(captured);
            resetCapturedPieceCache();
        }

        // 7. Handle castling — king moves 2 squares horizontally
        castlingP = null;
        if (activeP.type == PieceType.KING && Math.abs(toCol - fromCol) == 2) {
            // Rook is on the far side in the direction the king moved
            boolean kingSide = toCol > fromCol;
            int rookFromCol = kingSide ? 7 : 0;
            int rookToCol   = kingSide ? toCol - 1 : toCol + 1;
            for (Piece p : simPieces) {
                if (p.type == PieceType.ROOK && p.color == activeP.color && p.col == rookFromCol && p.row == fromRow) {
                    castlingP = p;
                    break;
                }
            }
            if (castlingP != null) {
                castlingP.col = rookToCol;
                castlingP.row = fromRow;
                castlingP.updatePixelPosition();
            }
        }

        // 8. Build MoveRecord fields — store in absolute (unflipped) coordinates.
        //    Must happen BEFORE updatePosition() overwrites preCol/preRow.
        boolean isCapture = captured != null;
        boolean isCastling = castlingP != null;
        String san = generateSAN(activeP, toCol, toRow, isCapture, isCastling);

        int recordFromCol = boardFlipped ? 7 - fromCol : fromCol;
        int recordFromRow = boardFlipped ? 7 - fromRow : fromRow;
        int recordToCol   = boardFlipped ? 7 - toCol   : toCol;
        int recordToRow   = boardFlipped ? 7 - toRow   : toRow;

        // 9. Commit pieces state (apply simPieces → pieces)
        copyPieces(simPieces, pieces);

        // 10. Update activeP state (moved, twoStepped, preCol/preRow)
        activeP.updatePosition();

        // 11. Handle promotion — replace pawn with promoted piece
        if (activeP.type == PieceType.PAWN && (toRow == 0 || toRow == 7)) {
            if (promoType == null) promoType = PieceType.QUEEN;
            Piece promoPiece;
            switch (promoType) {
                case ROOK:   promoPiece = new Rook(toCol, toRow, currentColor); break;
                case KNIGHT: promoPiece = new Knight(toCol, toRow, currentColor); break;
                case BISHOP: promoPiece = new Bishop(toCol, toRow, currentColor); break;
                default:     promoPiece = new Queen(toCol, toRow, currentColor); break;
            }
            pieces.remove(activeP);
            pieces.add(promoPiece);
            copyPieces(pieces, simPieces);
            san += "=" + promoType.getNotation();
        }

        // 12. Save history snapshot
        historyManager.saveHistorySnapshot();

        // 13. Add animations
        animations.add(new PieceAnimation(activeP, fromCol, fromRow, toCol, toRow));
        if (castlingP != null) {
            animations.add(new PieceAnimation(castlingP, castlingP.preCol, castlingP.preRow, castlingP.col, castlingP.row));
        }

        // 14. Build and register MoveRecord
        MoveRecord record = new MoveRecord(activeP.type, recordFromCol, recordFromRow, recordToCol, recordToRow, currentColor, isCapture, isCastling, san);
        if (promoType != null) {
            record.promotionType = promoType.toString();
        }
        if (moveStartTimeMillis > 0) {
            record.timeSpentSeconds = (int) ((System.currentTimeMillis() - moveStartTimeMillis) / 1000);
        }
        moveStartTimeMillis = System.currentTimeMillis();
        moves.add(record);

        // 15. Play sound
        if (isCapture) {
            SoundManager.playCapture();
        } else {
            SoundManager.playMove();
        }

        // 16. Clear castlingP now (after animation was added)
        castlingP = null;

        // 17. Finish the turn
        legalMoveSquares.clear();
        activeP = null;
        finishTurn();
    }

    public void makeValidatedEngineMove(int fromCol, int fromRow, int toCol, int toRow, PieceType promoType) {
        historyManager.viewEnd();
        historyManager.capturePreMoveSnapshot();

        Piece movingPiece = getPieceAt(fromCol, fromRow);
        if (movingPiece == null || movingPiece.color != currentColor) {
            System.err.println("Engine move rejected: no current-color piece at from-square.");
            historyManager.clearPreMoveSnapshot();
            return;
        }

        Piece target = getPieceAt(toCol, toRow);
        if (target != null && target.color == movingPiece.color) {
            System.err.println("Engine move rejected: target has friendly piece.");
            historyManager.clearPreMoveSnapshot();
            return;
        }

        if (!moveValidator.canLegallyMove(movingPiece, toCol, toRow)) {
            System.err.println("Engine move rejected as illegal: "
                    + fromCol + "," + fromRow + " -> " + toCol + "," + toRow);
            historyManager.clearPreMoveSnapshot();
            return;
        }

        activeP = movingPiece;
        PieceType movingType = movingPiece.type;
        int movingColor = currentColor;
        boolean isCastling = movingPiece.type == PieceType.KING && Math.abs(toCol - fromCol) == 2;
        Piece captured = target;

        if (captured == null && movingPiece.type == PieceType.PAWN && fromCol != toCol) {
            captured = getPieceAt(toCol, fromRow);
        }

        boolean isCapture = captured != null && captured.color != movingColor;
        String san = generateSAN(movingPiece, toCol, toRow, isCapture, isCastling);

        int recordFromCol = boardFlipped ? 7 - fromCol : fromCol;
        int recordFromRow = boardFlipped ? 7 - fromRow : fromRow;
        int recordToCol = boardFlipped ? 7 - toCol : toCol;
        int recordToRow = boardFlipped ? 7 - toRow : toRow;

        historyManager.saveHistorySnapshot();

        if (captured != null) {
            pieces.remove(captured);
            capturedPieces.add(captured);
            resetCapturedPieceCache();
        }

        castlingP = null;
        if (isCastling) {
            boolean kingSide = toCol > fromCol;
            int rookFromCol = kingSide ? 7 : 0;
            int rookToCol = kingSide ? toCol - 1 : toCol + 1;
            for (Piece p : pieces) {
                if (p.type == PieceType.ROOK && p.color == movingColor && p.col == rookFromCol && p.row == fromRow) {
                    castlingP = p;
                    break;
                }
            }
            if (castlingP != null) {
                castlingP.col = rookToCol;
                castlingP.row = fromRow;
                castlingP.updatePixelPosition();
            }
        }

        movingPiece.col = toCol;
        movingPiece.row = toRow;
        movingPiece.updatePosition();

        Piece animatedPiece = movingPiece;
        if (movingPiece.type == PieceType.PAWN && (toRow == 0 || toRow == 7)) {
            if (promoType == null) {
                promoType = PieceType.QUEEN;
            }
            Piece promoPiece;
            switch (promoType) {
                case ROOK: promoPiece = new Rook(toCol, toRow, movingColor); break;
                case KNIGHT: promoPiece = new Knight(toCol, toRow, movingColor); break;
                case BISHOP: promoPiece = new Bishop(toCol, toRow, movingColor); break;
                default: promoPiece = new Queen(toCol, toRow, movingColor); break;
            }
            promoPiece.moved = true;
            promoPiece.preCol = toCol;
            promoPiece.preRow = toRow;
            pieces.remove(movingPiece);
            pieces.add(promoPiece);
            animatedPiece = promoPiece;
            san += "=" + promoType.getNotation();
        }

        copyPieces(pieces, simPieces);

        animations.add(new PieceAnimation(animatedPiece, fromCol, fromRow, toCol, toRow));
        if (castlingP != null) {
            animations.add(new PieceAnimation(castlingP, castlingP.preCol, castlingP.preRow, castlingP.col, castlingP.row));
        }

        MoveRecord record = new MoveRecord(movingType, recordFromCol, recordFromRow, recordToCol, recordToRow, movingColor, isCapture, isCastling, san);
        if (promoType != null) {
            record.promotionType = promoType.toString();
        }
        if (moveStartTimeMillis > 0) {
            record.timeSpentSeconds = (int) ((System.currentTimeMillis() - moveStartTimeMillis) / 1000);
        }
        moveStartTimeMillis = System.currentTimeMillis();
        moves.add(record);

        if (isCapture) {
            SoundManager.playCapture();
        } else {
            SoundManager.playMove();
        }

        castlingP = null;
        legalMoveSquares.clear();
        activeP = null;
        finishTurn();
    }

    /**
     * Returns the piece at an absolute (unflipped) board coordinate.
     * Pieces in 'pieces' are stored in display coords, so we un-flip if needed.
     */
    private Piece getPieceAtAbsolute(int absCol, int absRow) {
        return getPieceAtAbsolute(pieces, absCol, absRow);
    }

    private Piece getPieceAtAbsolute(ArrayList<Piece> position, int absCol, int absRow) {
        int col = boardFlipped ? 7 - absCol : absCol;
        int row = boardFlipped ? 7 - absRow : absRow;
        for (Piece piece : position) {
            if (piece.col == col && piece.row == row) {
                return piece;
            }
        }
        return null;
    }



    // Stockfish expects standard FEN metadata so it can evaluate en passant and move counters correctly.
    private static final boolean STOCKFISH_STABLE_FEN = false;

    /**
     * Returns a FEN string for the currently viewed position (historical or live).
     * When the user has navigated through move history, this returns the FEN
     * of the displayed board state rather than the live position.
     */
    public String getViewFEN() {
        // For historical positions, derive the side-to-move from the move index
        // so the FEN's active-color field is correct. viewMoveIndex == -1 means
        // the live position, which already has the correct currentColor.
        int viewMoveIndex = getViewMoveIndex();
        int viewColor = viewMoveIndex == -1
                ? currentColor
                : ((viewMoveIndex % 2 == 0) ? WHITE : BLACK);
        int moveCount = viewMoveIndex == -1 ? moves.size() : viewMoveIndex;
        return buildFEN(new ArrayList<>(getDisplayPieces()), viewColor, moveCount);
    }

    public String getFEN() {
        return buildFEN(pieces, currentColor, moves.size());
    }

    private String buildFEN(ArrayList<Piece> position, int sideToMove, int moveCount) {
        StringBuilder fen = new StringBuilder();

        // Build piece placement (ranks 8 to 1, row 0 = rank 8, row 7 = rank 1)
        for (int rank = 8; rank >= 1; rank--) {
            int row = 8 - rank; // Convert rank to internal row
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = getPieceAtAbsolute(position, col, row);
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
        fen.append(' ').append(sideToMove == WHITE ? 'w' : 'b');

        // Castling rights
        StringBuilder castling = new StringBuilder();
        if (hasCastlingRights(position, WHITE, true))  castling.append('K');
        if (hasCastlingRights(position, WHITE, false)) castling.append('Q');
        if (hasCastlingRights(position, BLACK, true))  castling.append('k');
        if (hasCastlingRights(position, BLACK, false)) castling.append('q');
        if (castling.length() == 0) castling.append('-');
        fen.append(' ').append(castling);

        // En passant target
        // Stockfish integration stability: you may need en-passant/counters to be conservative.
        // When enabled, force en-passant to '-' and use safe clocks.
        String enPassant;
        int halfmove;
        int fullmove;

        if (STOCKFISH_STABLE_FEN) {
            enPassant = "-";
            halfmove = 0;
            fullmove = 1;
        } else {
            enPassant = getEnPassantSquare(moveCount);
            halfmove = getHalfmoveClock(moveCount);
            fullmove = moveCount / 2 + 1;
        }

        fen.append(' ').append(enPassant);
        fen.append(' ').append(halfmove);
        fen.append(' ').append(fullmove);


        return fen.toString();
    }

    public String getPGN() {
        StringBuilder pgn = new StringBuilder();
        
        // Add headers
        String whitePlayer = "White";
        String blackPlayer = "Black";
        String event = "Chess Game";
        String site = "Local";
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        String round = "1";
        String result = getGameResultString();
        
        pgn.append("[Event \"").append(event).append("\"]\n");
        pgn.append("[Site \"").append(site).append("\"]\n");
        pgn.append("[Date \"").append(date).append("\"]\n");
        pgn.append("[Round \"").append(round).append("\"]\n");
        pgn.append("[White \"").append(whitePlayer).append("\"]\n");
        pgn.append("[Black \"").append(blackPlayer).append("\"]\n");
        pgn.append("[Result \"").append(result).append("\"]\n\n");
        
        // Add move text
        int totalMoves = moves.size();
        int totalPairs = (totalMoves + 1) / 2;
        
        for (int i = 0; i < totalPairs; i++) {
            int moveNumber = i + 1;
            pgn.append(moveNumber).append(". ");
            
            // White move
            MoveRecord whiteMove = moves.get(i * 2);
            pgn.append(whiteMove.san);
            
            // Black move (if exists)
            if (i * 2 + 1 < totalMoves) {
                MoveRecord blackMove = moves.get(i * 2 + 1);
                pgn.append(" ").append(blackMove.san);
            }
            
            pgn.append(" ");
            
            // Add result every 8 moves for readability (optional)
            if ((i + 1) % 8 == 0) {
                pgn.append("\n");
            }
        }
        
        pgn.append(result);
        
        return pgn.toString();
    }
    
    private String getGameResultString() {
        if (whiteResign) {
            return "0-1";
        } else if (blackResign) {
            return "1-0";
        } else if (timeOutWinner != null) {
            if (timeOutWinner == WHITE) {
                return "1-0";
            } else {
                return "0-1";
            }
        } else if (stalemate) {
            return "1/2-1/2";
        } else if (gameOver) {
            // Checkmate - currentColor won (the side that just delivered checkmate wins)
            if (currentColor == WHITE) {
                return "1-0";
            } else {
                return "0-1";
            }
        }
        return "*";
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
        return hasCastlingRights(pieces, color, kingSide);
    }

    private boolean hasCastlingRights(ArrayList<Piece> position, int color, boolean kingSide) {
        // Find king
        Piece king = null;
        for (Piece p : position) {
            if (p.type == PieceType.KING && p.color == color) {
                king = p;
                break;
            }
        }
        if (king == null || king.moved) return false;

        // Find relevant rook
        int rookCol = kingSide ? 7 : 0;
        for (Piece p : position) {
            int absCol = boardFlipped ? 7 - p.col : p.col;
            if (p.type == PieceType.ROOK && p.color == color && absCol == rookCol) {
                return !p.moved;
            }
        }
        return false;
    }

    private String getEnPassantSquare(int moveCount) {
        int boundedMoveCount = Math.max(0, Math.min(moveCount, moves.size()));
        if (boundedMoveCount == 0) {
            return "-";
        }
        MoveRecord lastMove = moves.get(boundedMoveCount - 1);
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

    private int getHalfmoveClock(int moveCount) {
        int boundedMoveCount = Math.max(0, Math.min(moveCount, moves.size()));
        int count = 0;
        for (int i = boundedMoveCount - 1; i >= 0; i--) {
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

    public void viewMove(int moveCount) {
        historyManager.viewMove(moveCount);
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
