package com.jchess.view;

import com.jchess.mode.AnalysisModeController;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.Window;

final class AnalysisSettingsDialog {
    private AnalysisSettingsDialog() {
    }

    static void show(GamePanel parent, AnalysisModeController controller) {
        Window owner = javax.swing.SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Analysis Settings", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel fields = new JPanel(new GridLayout(4, 2, 10, 12));

        JSlider time = slider(2, 60, controller.getSearchTimeSeconds());
        JSlider multiPv = slider(1, 4, controller.getMultiPv());
        JSlider threads = slider(1, 4, controller.getThreads());
        JSlider memory = slider(16, 512, controller.getMemoryMb());
        fields.add(new JLabel("Search time (seconds)"));
        fields.add(time);
        fields.add(new JLabel("Multiple lines"));
        fields.add(multiPv);
        fields.add(new JLabel("Threads"));
        fields.add(threads);
        fields.add(new JLabel("Memory (MB)"));
        fields.add(memory);

        JButton cancel = new JButton("Cancel");
        JButton apply = new JButton("Apply");
        cancel.addActionListener(e -> dialog.dispose());
        apply.addActionListener(e -> {
            controller.setAnalysisSettings(time.getValue(), multiPv.getValue(), threads.getValue(), memory.getValue());
            parent.repaint();
            dialog.dispose();
        });
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(apply);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static JSlider slider(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, value);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setMajorTickSpacing(max <= 4 ? 1 : (max - min) / 4);
        return slider;
    }
}
