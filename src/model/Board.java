package model;

import java.awt.*;

public class Board {
	
	// Basic attribute of the board
    private static final int COL = 8;
    private static final int ROW = 8;
    public static final int SIZE = 75;
    
    // Board color
    private static final Color LIGHT = new Color(227, 171, 117);
    private static final Color DARK = new Color(201, 138, 18);
    
    // Text colors
    private static final Color TEXT_ON_LIGHT = new Color(139, 94, 43);
    private static final Color TEXT_ON_DARK = new Color(245, 222, 179);

    public void draw(Graphics2D g2) {
        
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int r = 0; r < ROW; r++) {
            for (int c = 0; c < COL; c++) {
                boolean isLight = (r + c) % 2 == 0;
                
                g2.setColor(isLight ? LIGHT : DARK);
                g2.fillRect(c * SIZE, r * SIZE, SIZE, SIZE);
                
                String file = (char) ('a' + c) + "";       
                String rank = (8 - r) + ""; 
                
                g2.setColor(isLight ? TEXT_ON_LIGHT : TEXT_ON_DARK);
                
                if (r == 7) {
                	g2.drawString(file, c * SIZE + 5, r * SIZE + 70);
                }
                
                if (c == 7) {
                	g2.drawString(rank, c * SIZE + 65, r * SIZE + 15);
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
