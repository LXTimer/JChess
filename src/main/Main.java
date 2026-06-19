package main;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;

import view.GamePanel;
import view.TitlePanel;

public class Main {

	public static void main(String[] args) {

		JFrame frame = new JFrame("JChess");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);

		GamePanel gamePanel = new GamePanel();
		TitlePanel titlePanel = new TitlePanel(gamePanel);

		JLayeredPane layeredPane = new JLayeredPane();
		layeredPane.setPreferredSize(gamePanel.getPreferredSize());

		gamePanel.setBounds(0, 0, gamePanel.getPreferredSize().width, gamePanel.getPreferredSize().height);
		titlePanel.setBounds(0, 0, gamePanel.getPreferredSize().width, gamePanel.getPreferredSize().height);

		layeredPane.add(gamePanel, JLayeredPane.DEFAULT_LAYER);
		layeredPane.add(titlePanel, JLayeredPane.PALETTE_LAYER);

		frame.add(layeredPane);
		frame.pack();

		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		
	}

}