package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TitlePanel extends JPanel {
    private GamePanel gamePanel;
    private JButton startButton;

    public TitlePanel(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
        setLayout(null);
        setBackground(new Color(40, 40, 40));
        setPreferredSize(new Dimension(800, 800));

        // Create blurred background area
        JLabel titleLabel = new JLabel("JChess");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 80));
        titleLabel.setForeground(new Color(200, 200, 200));
        titleLabel.setBounds(200, 200, 400, 150);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel);

        // Create start button
        startButton = new JButton("Start Game");
        startButton.setFont(new Font("Arial", Font.PLAIN, 24));
        startButton.setBounds(250, 450, 300, 60);
        startButton.setBackground(new Color(100, 150, 200));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startGame();
            }
        });
        add(startButton);
    }
    
    // Start the game and hide the panel
    private void startGame() {
        gamePanel.startGame();
        setVisible(false);
    }
}