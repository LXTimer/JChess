package com.jchess.input;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Mouse extends MouseAdapter {

    public int x, y;
    public boolean pressed;       // left button (or any non-right button)
    public boolean rightPressed;  // right button only

    @Override
    public void mousePressed(MouseEvent e) {
        x = e.getX();
        y = e.getY();
        if (e.getButton() == MouseEvent.BUTTON3) {
            rightPressed = true;
        } else {
            pressed = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        x = e.getX();
        y = e.getY();
        if (e.getButton() == MouseEvent.BUTTON3) {
            rightPressed = false;
        } else {
            pressed = false;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        x = e.getX();
        y = e.getY();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        x = e.getX();
        y = e.getY();
    }
}
