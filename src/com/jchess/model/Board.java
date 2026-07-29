package com.jchess.model;

import java.awt.*;

import com.jchess.util.GameSettings;

public class Board {
	
	// Basic attribute of the board
    private static final int COL = 8;
    private static final int ROW = 8;
    public static final int ORIGIN_X = 20;
    public static final int ORIGIN_Y = 20;
    public static final int SIZE = 70;
    
    public void draw(Graphics2D g2) {
        draw(g2, false);
    }

    public void draw(Graphics2D g2, boolean flipped) {
        GameSettings.BoardStyle style = GameSettings.getBoardStyle();
        
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int r = 0; r < ROW; r++) {
            for (int c = 0; c < COL; c++) {
                boolean isLight = (r + c) % 2 == 0;
                
                g2.setColor(isLight ? style.getLightSquare() : style.getDarkSquare());
                g2.fillRect(ORIGIN_X + c * SIZE, ORIGIN_Y + r * SIZE, SIZE, SIZE);
                
                // When flipped, invert the coordinate labels
                int displayCol = flipped ? 7 - c : c;
                int displayRow = flipped ? 7 - r : r;
                
                String file = (char) ('a' + displayCol) + "";       
                String rank = (8 - displayRow) + ""; 
                
                g2.setColor(isLight ? style.getLightLabel() : style.getDarkLabel());
                
                if (r == 7) {
	                g2.drawString(file, ORIGIN_X + c * SIZE + 5, ORIGIN_Y + r * SIZE + SIZE - 6);
                }
                
                if (c == 7) {
	                g2.drawString(rank, ORIGIN_X + c * SIZE + SIZE - 12, ORIGIN_Y + r * SIZE + 15);
                }
            }
        }
        
    }
    
    public int getCol() { 
    	return COL; 
    	
    }
    public int getRow() { 
    	return ROW; 
    	
    }
}
