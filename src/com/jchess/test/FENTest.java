package com.jchess.test;

import com.jchess.game.GameManager;
import com.jchess.input.Mouse;

public class FENTest {
    public static void main(String[] args) {
        Mouse mouse = new Mouse();
        GameManager gm = new GameManager(mouse);
        
        System.out.println("Initial position FEN:");
        System.out.println(gm.getFEN());
        System.out.println();
        
        // Expected: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
    }
}