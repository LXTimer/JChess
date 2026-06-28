package com.jchess.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import com.jchess.view.GamePanel;


public class TitlePanel extends JPanel {
    private GamePanel gamePanel;
    private JButton startButton;
    private JComboBox<String> timeComboBox;
    private JComboBox<String> colorComboBox;
    public boolean isPlayerWhite = true; // Default to white, can be changed based on selection

    // Fade effect fields
    private float alpha = 1.0f;
    private boolean fadingOut = false;
    private Timer fadeTimer;
    private BufferedImage iconImage;
    private BufferedImage backgroundImage;


    public TitlePanel(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
        setLayout(null);
        setBackground(new Color(70, 70, 70, 240));
        setOpaque(false);
        setPreferredSize(new Dimension(800, 800));

        // Load background image
        try {
            iconImage = ImageIO.read(getClass().getResource("/com/jchess/resources/icons/JChess.png"));
        } catch (IOException e) {
            System.out.println("Failed to load icon image");
        }  


        // Create title
        JLabel titleLabel = new JLabel("JChess");
        titleLabel.setFont(new Font("Roboto", Font.BOLD, 80));
        titleLabel.setForeground(new Color(200, 200, 200));
        titleLabel.setBounds(250, 200, 400, 150);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel);

        // Subtitle
        JLabel subtitleLabel = new JLabel("A Java Chess Game");
        subtitleLabel.setFont(new Font("Roboto", Font.PLAIN, 24));
        subtitleLabel.setForeground(new Color(150, 150, 150));
        subtitleLabel.setBounds(250, 320, 400, 50);
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(subtitleLabel);

        // Time control selector
        String[] timeOptions = {"1 min", "3 min", "5 min", "10 min", "15 min", "30 min", "60 min"};
        timeComboBox = new JComboBox<>(timeOptions);
        timeComboBox.setFont(new Font("Roboto", Font.PLAIN, 18));
        timeComboBox.setBounds(325, 380, 250, 35);
        timeComboBox.setSelectedItem("10 min");
        timeComboBox.setBackground(new Color(60, 60, 60));
        timeComboBox.setForeground(Color.WHITE);
        add(timeComboBox);

        // Color selector
        String[] colorOptions = {"Random", "White", "Black"};
        colorComboBox = new JComboBox<>(colorOptions);
        colorComboBox.setFont(new Font("Roboto", Font.PLAIN, 18));
        colorComboBox.setBounds(325, 420, 250, 35);
        colorComboBox.setSelectedItem("Random");
        colorComboBox.setBackground(new Color(60, 60, 60));
        colorComboBox.setForeground(Color.WHITE);
        add(colorComboBox);

        // Create start button
        startButton = new JButton("Start Game");
        startButton.setFont(new Font("Roboto", Font.PLAIN, 24));
        startButton.setBounds(300, 480, 300, 60);
        startButton.setBackground(new Color(100, 150, 200));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setBorderPainted(false);
        startButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedMinutes = Integer.parseInt((timeComboBox.getSelectedItem()).toString().split(" ")[0]);
                String selectedColor = (String) colorComboBox.getSelectedItem();
                if (selectedColor.equals("Random")) {
                    isPlayerWhite = Math.random() < 0.5;
                } else {
                    isPlayerWhite = selectedColor.equals("White");
                }
                gamePanel.setPlayerColor(isPlayerWhite);
                startGame(selectedMinutes * 60);
            }

        });

        startButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                startButton.setBackground(new Color(120, 170, 220));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                startButton.setBackground(new Color(100, 150, 200));
            }
        });

        add(startButton);
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (iconImage != null) {
            g2.drawImage(iconImage, 350, 40, 200, 200, null);
        }
        g2.dispose();
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        super.paint(g2);
        g2.dispose();
    }

    // Start the fade-out animation, then start the game and hide the panel
    private void startGame(int initialTimeSeconds) {
        if (fadingOut) return;
        fadingOut = true;
        startButton.setEnabled(false);

        fadeTimer = new Timer(16, null); // ~60 FPS
        fadeTimer.addActionListener(new ActionListener() {
            private long startTime = -1;
            private static final long FADE_DURATION = 1000; 

            @Override
            public void actionPerformed(ActionEvent e) {
                if (startTime < 0) {
                    startTime = System.currentTimeMillis();
                }
                long elapsed = System.currentTimeMillis() - startTime;
                alpha = 1.0f - (float) elapsed / FADE_DURATION;
                if (alpha < 0.0f) {
                    alpha = 0.0f;
                }
                repaint();

                if (alpha <= 0.0f) {
                    fadeTimer.stop();
                    // Now actually start the game and hide this panel
                    gamePanel.startGame(initialTimeSeconds);
                    setVisible(false);

                    // Reset so the panel fades in correctly if shown again later
                    alpha = 1.0f;
                    fadingOut = false;
                    startButton.setEnabled(true);
                }
            }
        });
        fadeTimer.setCoalesce(true);
        fadeTimer.start();
    }
}