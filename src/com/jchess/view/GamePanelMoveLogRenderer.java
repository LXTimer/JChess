package com.jchess.view;

import com.jchess.game.GameManager;
import com.jchess.input.Mouse;
import com.jchess.model.Board;
import com.jchess.model.Piece;
import com.jchess.model.piece.PieceType;
import com.jchess.util.MoveRecord;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class GamePanelMoveLogRenderer {
    private final GameManager gm;
    private final Mouse mouse;
    private final BufferedImage resignIcon;
    private final BufferedImage undoIcon;

    private final Rectangle menuButtonRect;
    private final Rectangle resignWhiteRect;
    private final Rectangle resignBlackRect;
    private final Rectangle undoWhiteRect;
    private final Rectangle undoBlackRect;
    private final Rectangle navStartRect;
    private final Rectangle navPrevRect;
    private final Rectangle navNextRect;
    private final Rectangle navEndRect;

    private static final int SIDE_PANEL_X = Board.ORIGIN_X + Board.SIZE * 8 + 10;
    private static final int SIDE_PANEL_Y = 35;
    private static final int SIDE_PANEL_WIDTH = 300;
    private static final int SIDE_PANEL_HEIGHT = 525;

    private static final Font FONT_ROBOTO_BOLD_12 = new Font("Roboto", Font.BOLD, 12);
    private static final Font FONT_ROBOTO_BOLD_11 = new Font("Roboto", Font.BOLD, 11);
    private static final Font FONT_SEG_UI_PLAIN_13 = new Font("Segoe UI Symbol", Font.PLAIN, 13);
    private static final Font FONT_SEG_UI_BOLD_13 = new Font("Segoe UI Symbol", Font.BOLD, 13);

    // Unicode chess symbols for piece types
    private static final String[] UNICODE_PIECES = {
        "\u2659", // White Pawn
        "\u2658", // White Knight
        "\u2657", // White Bishop
        "\u2656", // White Rook
        "\u2655", // White Queen
        "\u2654", // White King
        "\u265F", // Black Pawn
        "\u265E", // Black Knight
        "\u265D", // Black Bishop
        "\u265C", // Black Rook
        "\u265B", // Black Queen
        "\u265A"  // Black King
    };

    // Convert a SAN string to use Unicode chess symbols
    private String sanToUnicode(String san, int color) {
        if (san == null || san.isEmpty()) return san;
        
        // Handle castling - no piece symbol needed
        if (san.startsWith("O-O")) return san;
        
        // Get the first character which is the piece letter (K, Q, R, B, N)
        // Pawn moves don't have a letter prefix
        char firstChar = san.charAt(0);
        String unicodeSymbol = null;
        
        if (firstChar >= 'A' && firstChar <= 'Z') {
            // color == 0 (WHITE) -> white unicode pieces
            // color == 1 (BLACK) -> black unicode pieces
            switch (firstChar) {
                case 'K': unicodeSymbol = (color == 1) ? "\u265A" : "\u2654"; break; // King
                case 'Q': unicodeSymbol = (color == 1) ? "\u265B" : "\u2655"; break; // Queen
                case 'R': unicodeSymbol = (color == 1) ? "\u265C" : "\u2656"; break; // Rook
                case 'B': unicodeSymbol = (color == 1) ? "\u265D" : "\u2657"; break; // Bishop
                case 'N': unicodeSymbol = (color == 1) ? "\u265E" : "\u2658"; break; // Knight
            }
        }
        
        if (unicodeSymbol != null) {
            return unicodeSymbol + san.substring(1);
        }
        return san;
    }

    // Format time spent on a move 
    private String formatMoveTime(int seconds) {
        if (seconds <= 0) return "";
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + "m" + (secs < 10 ? "0" : "") + secs + "s";
    }

    public GamePanelMoveLogRenderer(
            GameManager gm,
            Mouse mouse,
            BufferedImage resignIcon,
            BufferedImage undoIcon,
            Rectangle menuButtonRect,
            Rectangle resignWhiteRect,
            Rectangle resignBlackRect,
            Rectangle undoWhiteRect,
            Rectangle undoBlackRect,
            Rectangle navStartRect,
            Rectangle navPrevRect,
            Rectangle navNextRect,
            Rectangle navEndRect) {
        this.gm = gm;
        this.mouse = mouse;
        this.resignIcon = resignIcon;
        this.undoIcon = undoIcon;
        this.menuButtonRect = menuButtonRect;
        this.resignWhiteRect = resignWhiteRect;
        this.resignBlackRect = resignBlackRect;
        this.undoWhiteRect = undoWhiteRect;
        this.undoBlackRect = undoBlackRect;
        this.navStartRect = navStartRect;
        this.navPrevRect = navPrevRect;
        this.navNextRect = navNextRect;
        this.navEndRect = navEndRect;
    }

    public void drawMoveLog(Graphics2D g2) {
        int boxX = SIDE_PANEL_X + 14;
        int boxY = SIDE_PANEL_Y + 110;
        int boxWidth = SIDE_PANEL_WIDTH - 28;
        int boxHeight = 305;
        int viewMoveIndex = gm.getViewMoveIndex();
        int totalMoves = gm.moves.size();
        int totalPairs = (totalMoves + 1) / 2;
        int activeMoveIndex = (viewMoveIndex == -1) ? (totalMoves - 1) : (viewMoveIndex - 1);
        boolean isFlipped = gm.isBoardFlipped();

        // Draw move log panel background with subtle gradient effect
        g2.setColor(new Color(8, 10, 14, 130));
        g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(255, 255, 255, 25));
        g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

        // Section header with subtle underline
        g2.setFont(FONT_ROBOTO_BOLD_12);
        g2.setColor(new Color(140, 150, 165));
        g2.drawString("MOVE LOG", boxX + 12, boxY + 20);
        g2.setColor(new Color(255, 255, 255, 12));
        g2.fillRect(boxX + 12, boxY + 26, boxWidth - 24, 1);

        // Single "Menu" dropdown button placed at the far right corner
        int menuButtonWidth = 50;
        int menuButtonHeight = 20;
        int menuButtonX = boxX + boxWidth - menuButtonWidth - 8;
        int buttonY = boxY + 4;
        menuButtonRect.setBounds(menuButtonX, buttonY, menuButtonWidth, menuButtonHeight);

        int resignButtonWidth = 30;
        int resignButtonHeight = 30;
        int resignButtonX = boxX + boxWidth - resignButtonWidth - 8;
        int undoButtonWidth = 30;
        int buttonSpacing = 6;
        int undoButtonX = resignButtonX - undoButtonWidth - buttonSpacing;
        int resignTopY = boxY + boxHeight - resignButtonHeight - 310;
        int resignBottomY = boxY + boxHeight - resignButtonHeight + 40;

        if (isFlipped) {
            resignWhiteRect.setBounds(resignButtonX, resignTopY, resignButtonWidth, resignButtonHeight);
            resignBlackRect.setBounds(resignButtonX, resignBottomY, resignButtonWidth, resignButtonHeight);
            undoWhiteRect.setBounds(undoButtonX, resignTopY, undoButtonWidth, resignButtonHeight);
            undoBlackRect.setBounds(undoButtonX, resignBottomY, undoButtonWidth, resignButtonHeight);
        } else {
            resignBlackRect.setBounds(resignButtonX, resignTopY, resignButtonWidth, resignButtonHeight);
            resignWhiteRect.setBounds(resignButtonX, resignBottomY, resignButtonWidth, resignButtonHeight);
            undoBlackRect.setBounds(undoButtonX, resignTopY, undoButtonWidth, resignButtonHeight);
            undoWhiteRect.setBounds(undoButtonX, resignBottomY, undoButtonWidth, resignButtonHeight);
        }

        int navButtonHeight = boxY + 54 - (boxY + 32);
        int navButtonWidth = (boxX + boxWidth - 10 - (boxX + 32)) / 4 + 5;
        int navButtonX1 = boxX + 10;
        int navButtonX2 = boxX + 10 + navButtonWidth;
        int navButtonX3 = boxX + 10 + 2 * navButtonWidth;
        int navButtonX4 = boxX + 10 + 3 * navButtonWidth;
        int navButtonY = boxY + 32;

        navStartRect.setBounds(navButtonX1, navButtonY, navButtonWidth, navButtonHeight);
        navPrevRect.setBounds(navButtonX2, navButtonY, navButtonWidth, navButtonHeight);
        navNextRect.setBounds(navButtonX3, navButtonY, navButtonWidth, navButtonHeight);
        navEndRect.setBounds(navButtonX4, navButtonY, navButtonWidth, navButtonHeight);

        boolean canGoStart = !gm.moves.isEmpty() && gm.getViewMoveIndex() != 0;
        boolean canGoPrev = !gm.moves.isEmpty() && (gm.getViewMoveIndex() > 0 || gm.getViewMoveIndex() == -1);
        boolean canGoNext = gm.getViewMoveIndex() != -1 && gm.getViewMoveIndex() < gm.moves.size();
        boolean canGoEnd = gm.getViewMoveIndex() != -1;
        boolean canUndo = gm.canUndo();
        boolean canResign = !gm.gameOver && !gm.stalemate && !gm.suppressGameOver;
        boolean hoverMenu = menuButtonRect.contains(mouse.x, mouse.y);
        boolean hoverUndoWhite = canUndo && undoWhiteRect.contains(mouse.x, mouse.y);
        boolean hoverUndoBlack = canUndo && undoBlackRect.contains(mouse.x, mouse.y);
        boolean hoverResignWhite = canResign && resignWhiteRect.contains(mouse.x, mouse.y);
        boolean hoverResignBlack = canResign && resignBlackRect.contains(mouse.x, mouse.y);

        // Draw the Menu button (disabled if game finished)
        boolean menuEnabled = !gm.gameOver && !gm.stalemate;
        g2.setColor(menuEnabled ? (hoverMenu ? new Color(76, 146, 220, 220) : new Color(52, 98, 155, 185)) : new Color(60, 60, 60, 160));
        g2.fillRoundRect(menuButtonX, buttonY, menuButtonWidth, menuButtonHeight, 4, 4);
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(1.5f));

        g2.setFont(FONT_ROBOTO_BOLD_11);
        FontMetrics menuMetrics = g2.getFontMetrics();
        int menuTextX = menuButtonX + (menuButtonWidth - menuMetrics.stringWidth("Menu")) / 2;
        int menuTextY = buttonY + (menuButtonHeight + menuMetrics.getAscent() - menuMetrics.getDescent()) / 2 - 1;
        g2.drawString("Menu", menuTextX, menuTextY);

        Color navHoverColor = new Color(100, 200, 150, 220);
        Color navBaseColor = new Color(20, 25, 35, 200);
        Color navDisabled = new Color(60, 60, 60, 120);

        Color undoBase = canUndo ? new Color(128, 128, 128, 200) : new Color(60, 60, 60, 140);
        g2.setColor(hoverUndoWhite ? navHoverColor : undoBase);
        g2.fillRoundRect(undoButtonX, undoWhiteRect.y, undoButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(hoverUndoBlack ? navHoverColor : undoBase);
        g2.fillRoundRect(undoButtonX, undoBlackRect.y, undoButtonWidth, resignButtonHeight, 4, 4);

        if (undoIcon != null) {
            java.awt.Composite oldComposite = g2.getComposite();
            if (!canUndo) {
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
            }
            g2.drawImage(undoIcon, undoButtonX + (undoButtonWidth - undoIcon.getWidth()) / 2,
                    undoWhiteRect.y + (resignButtonHeight - undoIcon.getHeight()) / 2, null);
            g2.drawImage(undoIcon, undoButtonX + (undoButtonWidth - undoIcon.getWidth()) / 2,
                    undoBlackRect.y + (resignButtonHeight - undoIcon.getHeight()) / 2, null);
            g2.setComposite(oldComposite);
        }

        Color resignBase = canResign ? new Color(128, 128, 128, 180) : new Color(60, 60, 60, 140);
        Color resignHover = canResign ? new Color(220, 80, 80, 220) : new Color(60, 60, 60, 140);
        g2.setColor(hoverResignWhite ? resignHover : resignBase);
        g2.fillRoundRect(resignButtonX, resignWhiteRect.y, resignButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(hoverResignBlack ? resignHover : resignBase);
        g2.fillRoundRect(resignButtonX, resignBlackRect.y, resignButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(new Color(200, 200, 200));


        if (resignIcon != null) {
            java.awt.Composite oldComposite = g2.getComposite();
            if (!canResign) {
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
            }
            g2.drawImage(resignIcon, resignButtonX + (resignButtonWidth - resignIcon.getWidth()) / 2,
                    resignWhiteRect.y + (resignButtonHeight - resignIcon.getHeight()) / 2, null);
            g2.drawImage(resignIcon, resignButtonX + (resignButtonWidth - resignIcon.getWidth()) / 2,
                    resignBlackRect.y + (resignButtonHeight - resignIcon.getHeight()) / 2, null);
            g2.setComposite(oldComposite);
        }

        g2.setColor(new Color(255, 255, 255, 20));
        g2.drawLine(boxX + 10, boxY + 32, boxX + boxWidth - 10, boxY + 32);

        int col1X = boxX + 15;
        int col2X = boxX + 70;
        int col3X = boxX + 150;
        int rowStartY = boxY + 48;
        int rowHeight = 22;

        int dividerY1 = boxY + 32;
        int dividerY2 = rowStartY + 6;
        g2.setColor(canGoStart ? (navStartRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor) : navDisabled);
        g2.fillRect(navButtonX1, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(canGoPrev ? (navPrevRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor) : navDisabled);
        g2.fillRect(navButtonX2, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(canGoNext ? (navNextRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor) : navDisabled);
        g2.fillRect(navButtonX3, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(canGoEnd ? (navEndRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor) : navDisabled);
        g2.fillRect(navButtonX4, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.drawLine(navButtonX2, dividerY1, navButtonX2, dividerY2);
        g2.drawLine(navButtonX3, dividerY1, navButtonX3, dividerY2);
        g2.drawLine(navButtonX4, dividerY1, navButtonX4, dividerY2);

        drawNaviTriangle(g2, navButtonX1 + navButtonWidth / 2 - 5, navButtonY + navButtonHeight / 2, "left", canGoStart);
        drawNaviTriangle(g2, navButtonX1 + navButtonWidth / 2 + 5, navButtonY + navButtonHeight / 2, "left", canGoStart);
        drawNaviTriangle(g2, navButtonX2 + navButtonWidth / 2, navButtonY + navButtonHeight / 2, "left", canGoPrev);
        drawNaviTriangle(g2, navButtonX3 + navButtonWidth / 2, navButtonY + navButtonHeight / 2, "right", canGoNext);
        drawNaviTriangle(g2, navButtonX4 + navButtonWidth / 2 - 5, navButtonY + navButtonHeight / 2, "right", canGoEnd);
        drawNaviTriangle(g2, navButtonX4 + navButtonWidth / 2 + 5, navButtonY + navButtonHeight / 2, "right", canGoEnd);

        g2.setColor(new Color(255, 255, 255, 15));
        g2.drawLine(boxX + 10, rowStartY + 6, boxX + boxWidth - 10, rowStartY + 6);

        int startY = rowStartY + 22;
        int maxVisible = 10;

        g2.setFont(FONT_SEG_UI_PLAIN_13);
        for (int i = 0; i < maxVisible; i++) {
            int pairIndex = gm.scrollStartLine + i;
            if (pairIndex >= totalPairs) {
                break;
            }

            int currentY = startY + i * rowHeight;

            // Alternating row background for better readability
            if (i % 2 == 0) {
                g2.setColor(new Color(255, 255, 255, 5));
                g2.fillRoundRect(col1X - 5, currentY - 15, col3X - col1X + 85, rowHeight, 3, 3);
            }

            g2.setColor(new Color(110, 120, 135));
            g2.drawString((pairIndex + 1) + ".", col1X, currentY);

            int whiteMoveIndex = pairIndex * 2;
            if (whiteMoveIndex < totalMoves) {
                MoveRecord whiteMove = gm.moves.get(whiteMoveIndex);
                String fullText = formatMoveLabel(whiteMove);
                boolean isBlunder = whiteMove.quality == MoveRecord.MoveQuality.BLUNDER;
                boolean isLastMove = (whiteMoveIndex == activeMoveIndex);
                boolean isHovered = isMoveHovered(whiteMoveIndex, mouse.x, mouse.y);
                Color moveColor = isBlunder ? new Color(230, 80, 80) : new Color(210, 215, 225);
                if (isHovered) {
                    g2.setColor(new Color(100, 200, 150, 90));
                    g2.fillRoundRect(col2X - 5, currentY - 14, 80, 18, 5, 5);
                    g2.setColor(moveColor);
                    g2.setFont(FONT_SEG_UI_BOLD_13);
                } else if (isLastMove) {
                    g2.setColor(new Color(0, 120, 215, 80));
                    g2.fillRoundRect(col2X - 5, currentY - 14, 80, 18, 5, 5);
                    g2.setColor(moveColor);
                    g2.setFont(FONT_SEG_UI_BOLD_13);
                } else {
                    g2.setColor(moveColor);
                    g2.setFont(FONT_SEG_UI_PLAIN_13);
                }
                g2.drawString(fullText, col2X, currentY);
            }

            int blackMoveIndex = pairIndex * 2 + 1;
            if (blackMoveIndex < totalMoves) {
                MoveRecord blackMove = gm.moves.get(blackMoveIndex);
                String fullText = formatMoveLabel(blackMove);
                boolean isBlunder = blackMove.quality == MoveRecord.MoveQuality.BLUNDER;
                boolean isLastMove = (blackMoveIndex == activeMoveIndex);
                boolean isHovered = isMoveHovered(blackMoveIndex, mouse.x, mouse.y);
                Color moveColor = isBlunder ? new Color(230, 80, 80) : new Color(210, 215, 225);
                if (isHovered) {
                    g2.setColor(new Color(100, 200, 150, 90));
                    g2.fillRoundRect(col3X - 5, currentY - 14, 80, 18, 5, 5);
                    g2.setColor(moveColor);
                    g2.setFont(FONT_SEG_UI_BOLD_13);
                } else if (isLastMove) {
                    g2.setColor(new Color(0, 120, 215, 80));
                    g2.fillRoundRect(col3X - 5, currentY - 14, 80, 18, 5, 5);
                    g2.setColor(moveColor);
                    g2.setFont(FONT_SEG_UI_BOLD_13);
                } else {
                    g2.setColor(moveColor);
                    g2.setFont(FONT_SEG_UI_PLAIN_13);
                }
                g2.drawString(fullText, col3X, currentY);
            }
        }

        if (totalPairs > maxVisible) {
            int scrollbarX = boxX + boxWidth - 8;
            int scrollbarY = startY - 12;
            int scrollbarHeight = boxHeight - (scrollbarY - boxY) - 10;
            int scrollbarWidth = 4;

            g2.setColor(new Color(255, 255, 255, 10));
            g2.fillRoundRect(scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight, 2, 2);

            int thumbHeight = scrollbarHeight * maxVisible / totalPairs;
            if (thumbHeight < 15) {
                thumbHeight = 15;
            }
            int thumbY = scrollbarY + (scrollbarHeight - thumbHeight) * gm.scrollStartLine / (totalPairs - maxVisible);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(scrollbarX, thumbY, scrollbarWidth, thumbHeight, 2, 2);
        }
    }

    public boolean handleMoveClick() {
        int moveIndex = getMoveIndexAt(mouse.x, mouse.y);
        if (moveIndex < 0 || moveIndex >= gm.moves.size()) {
            return false;
        }

        gm.viewMove(moveIndex + 1);
        return true;
    }

    public boolean isMoveHovered() {
        return getMoveIndexAt(mouse.x, mouse.y) >= 0;
    }

    private boolean isMoveHovered(int moveIndex, int mouseX, int mouseY) {
        return getMoveIndexAt(mouseX, mouseY) == moveIndex;
    }

    private int getMoveIndexAt(int mouseX, int mouseY) {
        int boxX = SIDE_PANEL_X + 14;
        int boxY = SIDE_PANEL_Y + 110;
        int col2X = boxX + 70;
        int col3X = boxX + 150;
        int startY = boxY + 48 + 22;
        int rowHeight = 22;
        int row = (mouseY - (startY - 15)) / rowHeight;
        if (row < 0 || row >= 10 || mouseY < startY - 15 || mouseY > startY + 9 * rowHeight + 3) {
            return -1;
        }

        int pairIndex = gm.scrollStartLine + row;
        if (mouseX >= col2X - 5 && mouseX < col3X - 5) {
            return pairIndex * 2;
        }
        if (mouseX >= col3X - 5 && mouseX < col3X + 75) {
            return pairIndex * 2 + 1;
        }
        return -1;
    }

    private String formatMoveLabel(MoveRecord move) {
        String displaySan = sanToUnicode(move.san, move.color);
        String timeStr = formatMoveTime(move.timeSpentSeconds);
        return timeStr.isEmpty() ? displaySan : displaySan + " " + timeStr;
    }

    private void drawNaviTriangle(Graphics2D g2, int x, int y, String direction, boolean enabled) {
        int[] xs;
        int[] ys = {y, y - 5, y + 5};
        if ("left".equals(direction)) {
            xs = new int[]{x - 5, x + 5, x + 5};
        } else {
            xs = new int[]{x + 5, x - 5, x - 5};
        }

        g2.setColor(enabled ? new Color(255, 255, 255, 200) : new Color(60, 60, 60, 160));
        g2.fillPolygon(xs, ys, 3);
    }
}
