package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;


public class TitlePanel extends JPanel {
    private GamePanel gamePanel;
    private JButton startButton;

    // Fade effect fields
    private float alpha = 1.0f;
    private boolean fadingOut = false;
    private Timer fadeTimer;
    private BufferedImage iconImage;
    private BufferedImage backgroundImage;

    public TitlePanel(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
        setLayout(null);
        setBackground(new Color(40, 40, 40));
        setPreferredSize(new Dimension(800, 800));

        // Load background image
        try {
            iconImage = ImageIO.read(getClass().getResource("/resources/icons/JChess.png"));
        } catch (IOException e) {
            System.out.println("Failed to load icon image");
        }  

        try {
            iconImage = ImageIO.read(getClass().getResource("/resources/title_background.jpg"));
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

        // Create start button
        startButton = new JButton("Start Game");
        startButton.setFont(new Font("Roboto", Font.PLAIN, 24));
        startButton.setBounds(300, 450, 300, 60);
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
        super.paintComponent(g);
        if (iconImage != null) {
                g.drawImage(iconImage, 350, 40, 200, 200, null);
        }

    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        super.paint(g2);
        g2.dispose();
    }

    // Start the fade-out animation, then start the game and hide the panel
    private void startGame() {
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
                    gamePanel.startGame();
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