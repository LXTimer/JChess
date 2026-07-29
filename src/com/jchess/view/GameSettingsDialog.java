package com.jchess.view;

import com.jchess.util.GameSettings;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;

public final class GameSettingsDialog {
    private GameSettingsDialog() {
    }

    public static void show(Component parent, GamePanel gamePanel) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Game Settings");
        title.setFont(new Font("Roboto", Font.BOLD, 20));
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 12));
        form.setOpaque(false);

        JComboBox<GameSettings.BoardStyle> boardStyleBox = new JComboBox<>(GameSettings.BoardStyle.values());
        boardStyleBox.setSelectedItem(GameSettings.getBoardStyle());

        JComboBox<GameSettings.PieceStyle> pieceStyleBox = new JComboBox<>(GameSettings.PieceStyle.values());
        pieceStyleBox.setSelectedItem(GameSettings.getPieceStyle());

        JSlider volumeSlider = new JSlider(0, 100, GameSettings.getVolumePercent());
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);

        form.add(makeLabel("Board style"));
        form.add(boardStyleBox);
        form.add(makeLabel("Piece style"));
        form.add(pieceStyleBox);
        form.add(makeLabel("Sound volume"));
        form.add(volumeSlider);

        content.add(form, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                parent,
                content,
                "Settings",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            GameSettings.setBoardStyle((GameSettings.BoardStyle) boardStyleBox.getSelectedItem());
            GameSettings.setPieceStyle((GameSettings.PieceStyle) pieceStyleBox.getSelectedItem());
            GameSettings.setVolumePercent(volumeSlider.getValue());
            if (gamePanel != null) {
                gamePanel.applySettings();
            }
        }
    }

    private static JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Roboto", Font.PLAIN, 14));
        return label;
    }
}