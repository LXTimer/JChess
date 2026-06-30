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
    private JButton helpButton;
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

        // Controls and tips button
        helpButton = new JButton("?");
        helpButton.setFont(new Font("Roboto", Font.PLAIN, 25));
        helpButton.setBounds(800, 35, 48, 48);
        helpButton.setBackground(new Color(56, 60, 68));
        helpButton.setForeground(Color.WHITE);
        helpButton.setFocusPainted(false);
        helpButton.setBorderPainted(false);
        helpButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        helpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showControlsDialog();
            }
        });

        helpButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                helpButton.setBackground(new Color(74, 80, 90));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                helpButton.setBackground(new Color(56, 60, 68));
            }
        });

        add(helpButton);
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

    public void showTitlePanel() {
        if (fadeTimer != null && fadeTimer.isRunning()) {
            fadeTimer.stop();
        }
        alpha = 1.0f;
        fadingOut = false;
        startButton.setEnabled(true);
        helpButton.setEnabled(true);
        setVisible(true);
        repaint();
    }

    private void showControlsDialog() {
        String message = "<html><div style='width: 420px; font-family: Roboto;'>"
                + "<h2 style='margin: 0 0 10px 0;'>Controls & Tips</h2>"
                + "<b>Basic Play</b><br>"
                + "- Click a piece to select it, then click a highlighted square to move.<br>"
                + "- Use the move log to review previous moves.<br>"
                + "- Right-click a square to toggle a circle marker.<br>"
                + "- Right-click and drag to draw an arrow.<br><br>"
                + "<b>Keyboard Shortcuts</b><br>"
                + "- Left Arrow: Previous move<br>"
                + "- Right Arrow: Next move<br>"
                + "- Up Arrow: Go to start<br>"
                + "- Down Arrow: Go to live position<br>"
                + "- F: Flip board<br>"
                + "- Ctrl + Z: Undo last move<br><br>"
                + "<b>Match</b><br>"
                + "- Choose a time control before starting.<br>"
                + "- Choose White, Black, or Random color.<br>"
                + "- When the game ends, use Restart or Return to Title.<br>"
                + "</div></html>";

        JOptionPane.showMessageDialog(
                this,
                message,
                "Controls & Tips",
                JOptionPane.INFORMATION_MESSAGE);
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