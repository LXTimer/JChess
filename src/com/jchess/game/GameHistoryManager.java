package com.jchess.game;

import com.jchess.model.Board;
import com.jchess.model.Piece;
import com.jchess.model.piece.Bishop;
import com.jchess.model.piece.King;
import com.jchess.model.piece.Knight;
import com.jchess.model.piece.Pawn;
import com.jchess.model.piece.PieceType;
import com.jchess.model.piece.Queen;
import com.jchess.model.piece.Rook;
import com.jchess.util.MoveRecord;

import java.util.ArrayList;

public class GameHistoryManager {
    private final GameManager gm;

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
    private int viewMoveIndex = -1;

    public GameHistoryManager(GameManager gm) {
        this.gm = gm;
    }

    private ArrayList<Piece> copyPieceList(ArrayList<Piece> source) {
        ArrayList<Piece> clone = new ArrayList<>();
        for (Piece piece : source) {
            clone.add(piece.copy());
        }
        return clone;
    }

    public void capturePreMoveSnapshot() {
        preMoveSnapshot = copyPieceList(gm.pieces);
    }

    public void saveHistorySnapshot() {
        piecesHistory.add(preMoveSnapshot != null ? preMoveSnapshot : copyPieceList(gm.pieces));
        simPiecesHistory.add(copyPieceList(gm.pieces));
        capturedPiecesHistory.add(copyPieceList(gm.capturedPieces));
        currentColorHistory.add(gm.currentColor);
        gameOverHistory.add(gm.gameOver);
        stalemateHistory.add(gm.stalemate);
        whiteResignHistory.add(gm.whiteResign);
        blackResignHistory.add(gm.blackResign);
        boardFlippedHistory.add(gm.boardFlipped);
        preMoveSnapshot = null;
    }

    public boolean canUndo() {
        return !gm.moves.isEmpty() && !piecesHistory.isEmpty();
    }

    public void undoLastMove() {
        if (!canUndo()) {
            return;
        }

        gm.clearAnimations();

        int lastIndex = piecesHistory.size() - 1;
        gm.pieces = piecesHistory.remove(lastIndex);
        gm.copyPieces(gm.pieces, GameManager.simPieces);
        gm.capturedPieces = capturedPiecesHistory.remove(lastIndex);
        gm.currentColor = currentColorHistory.remove(lastIndex);
        gm.gameOver = gameOverHistory.remove(lastIndex);
        gm.stalemate = stalemateHistory.remove(lastIndex);
        gm.whiteResign = whiteResignHistory.remove(lastIndex);
        gm.blackResign = blackResignHistory.remove(lastIndex);
        gm.boardFlipped = boardFlippedHistory.remove(lastIndex);
        GameManager.isBoardFlipped = gm.boardFlipped;

        if (!simPiecesHistory.isEmpty()) {
            simPiecesHistory.remove(simPiecesHistory.size() - 1);
        }

        if (!gm.moves.isEmpty()) {
            gm.moves.remove(gm.moves.size() - 1);
        }

        gm.activeP = null;
        gm.legalMoveSquares.clear();
        gm.promotion = false;
        gm.promoPieces.clear();
        GameManager.castlingP = null;
        gm.checkingP = null;
        preMoveSnapshot = null;
        gm.resetCapturedPieceCache();
        viewMoveIndex = -1;

        if (!gm.gameOver && !gm.stalemate) {
            gm.checkingP = gm.getMoveValidator().findCheckingPiece(gm.getOppositeColor(gm.currentColor));
        }

        gm.scrollToBottom();
    }

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
        if (viewMoveIndex < gm.moves.size()) {
            viewMoveIndex++;
        }
        if (viewMoveIndex >= gm.moves.size()) {
            viewMoveIndex = -1;
        }
    }

    public void viewPrevious() {
        if (viewMoveIndex == -1) {
            viewMoveIndex = gm.moves.size() - 1;
        } else if (viewMoveIndex > 0) {
            viewMoveIndex--;
        }
    }

    public int getViewMoveIndex() {
        return viewMoveIndex;
    }

    public void resetHistory() {
        piecesHistory.clear();
        simPiecesHistory.clear();
        capturedPiecesHistory.clear();
        currentColorHistory.clear();
        gameOverHistory.clear();
        stalemateHistory.clear();
        whiteResignHistory.clear();
        blackResignHistory.clear();
        boardFlippedHistory.clear();
        preMoveSnapshot = null;
        viewMoveIndex = -1;
    }

    public ArrayList<Piece> getDisplayPieces() {
        if (viewMoveIndex == -1) {
            return GameManager.simPieces;
        }
        return getBoardAtMoveCount(viewMoveIndex);
    }

    private ArrayList<Piece> getBoardAtMoveCount(int moveCount) {
        ArrayList<Piece> board = new ArrayList<>();
        for (int col = 0; col < 8; col++) {
            board.add(new Pawn(col, 6, GameManager.WHITE));
            board.add(new Pawn(col, 1, GameManager.BLACK));
        }

        int[] backRankCols = {0, 7, 1, 6, 2, 5, 3, 4};
        for (int i = 0; i < backRankCols.length; i++) {
            int col = backRankCols[i];
            Piece whitePiece;
            Piece blackPiece;
            if (i < 2) {
                whitePiece = new Rook(col, 7, GameManager.WHITE);
                blackPiece = new Rook(col, 0, GameManager.BLACK);
            } else if (i < 4) {
                whitePiece = new Knight(col, 7, GameManager.WHITE);
                blackPiece = new Knight(col, 0, GameManager.BLACK);
            } else if (i < 6) {
                whitePiece = new Bishop(col, 7, GameManager.WHITE);
                blackPiece = new Bishop(col, 0, GameManager.BLACK);
            } else if (i < 7) {
                whitePiece = new Queen(col, 7, GameManager.WHITE);
                blackPiece = new Queen(col, 0, GameManager.BLACK);
            } else {
                whitePiece = new King(col, 7, GameManager.WHITE);
                blackPiece = new King(col, 0, GameManager.BLACK);
            }
            board.add(whitePiece);
            board.add(blackPiece);
        }

        if (gm.boardFlipped) {
            for (Piece piece : board) {
                piece.col = 7 - piece.col;
                piece.row = 7 - piece.row;
                piece.preCol = 7 - piece.preCol;
                piece.preRow = 7 - piece.preRow;
                piece.x = piece.getX(piece.col);
                piece.y = piece.getY(piece.row);
            }
        }

        int applyCount = Math.max(0, Math.min(moveCount, gm.moves.size()));
        for (int i = 0; i < applyCount; i++) {
            applyMoveToBoard(board, gm.moves.get(i));
        }

        return board;
    }

    private void applyMoveToBoard(ArrayList<Piece> board, MoveRecord moveRecord) {
        int fromCol = gm.boardFlipped ? 7 - moveRecord.fromCol : moveRecord.fromCol;
        int fromRow = gm.boardFlipped ? 7 - moveRecord.fromRow : moveRecord.fromRow;
        int toCol = gm.boardFlipped ? 7 - moveRecord.toCol : moveRecord.toCol;
        int toRow = gm.boardFlipped ? 7 - moveRecord.toRow : moveRecord.toRow;

        Piece mover = null;
        for (Piece piece : board) {
            if (piece.col == fromCol && piece.row == fromRow && piece.color == moveRecord.color) {
                if (piece.type == moveRecord.type) {
                    mover = piece;
                    break;
                }
                if (mover == null) {
                    mover = piece;
                }
            }
        }

        if (mover == null) {
            return;
        }

        if (moveRecord.isCapture) {
            Piece captured = null;
            for (Piece piece : board) {
                if (piece.col == toCol && piece.row == toRow && piece.color != moveRecord.color) {
                    captured = piece;
                    break;
                }
            }
            if (captured == null) {
                for (Piece piece : board) {
                    if (piece.col == toCol && piece.row == fromRow && piece.color != moveRecord.color && piece.type == PieceType.PAWN) {
                        captured = piece;
                        break;
                    }
                }
            }
            if (captured != null) {
                board.remove(captured);
            }
        }

        if (moveRecord.isCastling && moveRecord.type == PieceType.KING) {
            int rookFromCol = gm.boardFlipped ? 7 - (moveRecord.toCol > moveRecord.fromCol ? 7 : 0) : (moveRecord.toCol > moveRecord.fromCol ? 7 : 0);
            int rookToCol = gm.boardFlipped ? 7 - (moveRecord.toCol > moveRecord.fromCol ? moveRecord.toCol - 1 : moveRecord.toCol + 1) : (moveRecord.toCol > moveRecord.fromCol ? moveRecord.toCol - 1 : moveRecord.toCol + 1);
            for (Piece piece : board) {
                if (piece.col == rookFromCol && piece.row == fromRow && piece.color == moveRecord.color && piece.type == PieceType.ROOK) {
                    piece.col = rookToCol;
                    piece.row = toRow;
                    piece.x = piece.getX(piece.col);
                    piece.y = piece.getY(piece.row);
                    break;
                }
            }
        }

        mover.col = toCol;
        mover.row = toRow;
        mover.x = mover.getX(mover.col);
        mover.y = mover.getY(mover.row);
        mover.moved = true;

        if (moveRecord.promotionType != null) {
            Piece promoted;
            switch (moveRecord.promotionType) {
                case "ROOK":
                    promoted = new Rook(mover.col, mover.row, mover.color);
                    break;
                case "KNIGHT":
                    promoted = new Knight(mover.col, mover.row, mover.color);
                    break;
                case "BISHOP":
                    promoted = new Bishop(mover.col, mover.row, mover.color);
                    break;
                default:
                    promoted = new Queen(mover.col, mover.row, mover.color);
                    break;
            }
            board.remove(mover);
            board.add(promoted);
        }
    }
}