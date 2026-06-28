package com.jchess.view;

import com.jchess.game.GameManager;
import com.jchess.input.Mouse;
import com.jchess.model.Board;
import com.jchess.model.Piece;
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
    private final BufferedImage flipBoardIcon;
    private final BufferedImage resignIcon;
    private final BufferedImage undoIcon;

    private final Rectangle flipButtonRect;
    private final Rectangle resignWhiteRect;
    private final Rectangle resignBlackRect;
    private final Rectangle undoWhiteRect;
    private final Rectangle undoBlackRect;
    private final Rectangle navStartRect;
    private final Rectangle navPrevRect;
    private final Rectangle navNextRect;
    private final Rectangle navEndRect;

    private static final int SIDE_PANEL_X = Board.SIZE * 8 + 12;
    private static final int SIDE_PANEL_Y = 45;
    private static final int SIDE_PANEL_WIDTH = GamePanel.WIDTH - SIDE_PANEL_X - 16;
    private static final int SIDE_PANEL_HEIGHT = 510;

    public GamePanelMoveLogRenderer(
            GameManager gm,
            Mouse mouse,
            BufferedImage flipBoardIcon,
            BufferedImage resignIcon,
            BufferedImage undoIcon,
            Rectangle flipButtonRect,
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
        this.flipBoardIcon = flipBoardIcon;
        this.resignIcon = resignIcon;
        this.undoIcon = undoIcon;
        this.flipButtonRect = flipButtonRect;
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
        int boxX = SIDE_PANEL_X + 16;
        int boxY = 150;
        int boxWidth = SIDE_PANEL_WIDTH - 32;
        int boxHeight = 300;

        g2.setColor(new Color(10, 12, 16, 120));
        g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(255, 255, 255, 30));
        g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

        g2.setFont(new Font("Roboto", Font.BOLD, 13));
        g2.setColor(new Color(160, 170, 185));
        g2.drawString("MOVE LOG", boxX + 12, boxY + 22);

        int buttonWidth = 30;
        int buttonHeight = 20;
        int buttonX = boxX + boxWidth - buttonWidth - 10;
        int buttonY = boxY + 5;
        flipButtonRect.setBounds(buttonX, buttonY, buttonWidth, buttonHeight);

        boolean isFlipped = gm.isBoardFlipped();
        int resignButtonWidth = 30;
        int resignButtonHeight = 30;
        int resignButtonX = boxX + boxWidth - resignButtonWidth - 10;
        int undoButtonWidth = 30;
        int buttonSpacing = 8;
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

        boolean canUndo = gm.canUndo();
        boolean hoverFlip = flipButtonRect.contains(mouse.x, mouse.y);
        boolean hoverUndoWhite = canUndo && undoWhiteRect.contains(mouse.x, mouse.y);
        boolean hoverUndoBlack = canUndo && undoBlackRect.contains(mouse.x, mouse.y);
        boolean hoverResignWhite = resignWhiteRect.contains(mouse.x, mouse.y);
        boolean hoverResignBlack = resignBlackRect.contains(mouse.x, mouse.y);

        g2.setColor(hoverFlip ? new Color(85, 170, 255, 220) : new Color(40, 115, 220, 180));
        g2.fillRoundRect(buttonX, buttonY, buttonWidth, buttonHeight, 4, 4);
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(1.5f));

        Color navHoverColor = new Color(120, 210, 120, 220);
        Color navBaseColor = new Color(0, 0, 0, 200);

        Color undoBase = canUndo ? new Color(128, 128, 128, 200) : new Color(80, 80, 80, 180);
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

        g2.setColor(hoverResignWhite ? new Color(220, 80, 80, 220) : new Color(128, 128, 128, 180));
        g2.fillRoundRect(resignButtonX, resignWhiteRect.y, resignButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(hoverResignBlack ? new Color(220, 80, 80, 220) : new Color(128, 128, 128, 180));
        g2.fillRoundRect(resignButtonX, resignBlackRect.y, resignButtonWidth, resignButtonHeight, 4, 4);
        g2.setColor(new Color(200, 200, 200));

        if (flipBoardIcon != null) {
            g2.drawImage(flipBoardIcon, buttonX + (buttonWidth - flipBoardIcon.getWidth()) / 2,
                    buttonY + (buttonHeight - flipBoardIcon.getHeight()) / 2, null);
        }

        if (resignIcon != null) {
            g2.drawImage(resignIcon, resignButtonX + (resignButtonWidth - resignIcon.getWidth()) / 2,
                    resignWhiteRect.y + (resignButtonHeight - resignIcon.getHeight()) / 2, null);
            g2.drawImage(resignIcon, resignButtonX + (resignButtonWidth - resignIcon.getWidth()) / 2,
                    resignBlackRect.y + (resignButtonHeight - resignIcon.getHeight()) / 2, null);
        }

        g2.setColor(new Color(255, 255, 255, 20));
        g2.drawLine(boxX + 10, boxY + 32, boxX + boxWidth - 10, boxY + 32);

        int col1X = boxX + 15;
        int col2X = boxX + 75;
        int col3X = boxX + 155;
        int rowStartY = boxY + 48;
        int rowHeight = 22;

        int dividerY1 = boxY + 32;
        int dividerY2 = rowStartY + 6;
        g2.setColor(navStartRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX1, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(navPrevRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX2, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(navNextRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX3, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(navEndRect.contains(mouse.x, mouse.y) ? navHoverColor : navBaseColor);
        g2.fillRect(navButtonX4, navButtonY, navButtonWidth, navButtonHeight);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.drawLine(navButtonX2, dividerY1, navButtonX2, dividerY2);
        g2.drawLine(navButtonX3, dividerY1, navButtonX3, dividerY2);
        g2.drawLine(navButtonX4, dividerY1, navButtonX4, dividerY2);

        drawNaviTriangle(g2, navButtonX1 + navButtonWidth / 2 - 5, navButtonY + navButtonHeight / 2, "left");
        drawNaviTriangle(g2, navButtonX1 + navButtonWidth / 2 + 5, navButtonY + navButtonHeight / 2, "left");
        drawNaviTriangle(g2, navButtonX2 + navButtonWidth / 2, navButtonY + navButtonHeight / 2, "left");
        drawNaviTriangle(g2, navButtonX3 + navButtonWidth / 2, navButtonY + navButtonHeight / 2, "right");
        drawNaviTriangle(g2, navButtonX4 + navButtonWidth / 2 - 5, navButtonY + navButtonHeight / 2, "right");
        drawNaviTriangle(g2, navButtonX4 + navButtonWidth / 2 + 5, navButtonY + navButtonHeight / 2, "right");

        g2.setColor(new Color(255, 255, 255, 15));
        g2.drawLine(boxX + 10, rowStartY + 6, boxX + boxWidth - 10, rowStartY + 6);

        int startY = rowStartY + 22;
        int totalMoves = gm.moves.size();
        int totalPairs = (totalMoves + 1) / 2;
        int maxVisible = 10;

        g2.setFont(new Font("Roboto", Font.PLAIN, 13));
        int activeMoveIndex = (gm.getViewMoveIndex() == -1) ? (totalMoves - 1) : (gm.getViewMoveIndex() - 1);
        for (int i = 0; i < maxVisible; i++) {
            int pairIndex = gm.scrollStartLine + i;
            if (pairIndex >= totalPairs) {
                break;
            }

            int currentY = startY + i * rowHeight;

            g2.setColor(new Color(110, 120, 135));
            g2.drawString((pairIndex + 1) + ".", col1X, currentY);

            int whiteMoveIndex = pairIndex * 2;
            if (whiteMoveIndex < totalMoves) {
                MoveRecord whiteMove = gm.moves.get(whiteMoveIndex);
                boolean isLastMove = (whiteMoveIndex == activeMoveIndex);
                if (isLastMove) {
                    g2.setColor(new Color(0, 120, 215, 60));
                    g2.fillRoundRect(col2X - 5, currentY - 14, 65, 18, 4, 4);
                    g2.setColor(new Color(255, 255, 255));
                } else {
                    g2.setColor(new Color(210, 215, 225));
                }
                g2.drawString(whiteMove.san, col2X, currentY);
            }

            int blackMoveIndex = pairIndex * 2 + 1;
            if (blackMoveIndex < totalMoves) {
                MoveRecord blackMove = gm.moves.get(blackMoveIndex);
                boolean isLastMove = (blackMoveIndex == activeMoveIndex);
                if (isLastMove) {
                    g2.setColor(new Color(0, 120, 215, 60));
                    g2.fillRoundRect(col3X - 5, currentY - 14, 65, 18, 4, 4);
                    g2.setColor(new Color(255, 255, 255));
                } else {
                    g2.setColor(new Color(210, 215, 225));
                }
                g2.drawString(blackMove.san, col3X, currentY);
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

    private void drawNaviTriangle(Graphics2D g2, int x, int y, String direction) {
        int[] xs;
        int[] ys = {y, y - 5, y + 5};
        if ("left".equals(direction)) {
            xs = new int[]{x - 5, x + 5, x + 5};
        } else {
            xs = new int[]{x + 5, x - 5, x - 5};
        }

        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillPolygon(xs, ys, 3);
    }
}