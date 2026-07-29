package com.jchess.util;

import java.awt.Color;

public final class GameSettings {
    public enum PieceStyle {
        ALPHA("Alpha", "alpha"),
        NEO("Neo", "neo"),
        WOOD("Wood", "wood");

        private final String displayName;
        private final String directory;

        PieceStyle(String displayName, String directory) {
            this.displayName = displayName;
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum BoardStyle {
        CLASSIC("Classic", new Color(227, 171, 117), new Color(201, 138, 18),
                new Color(139, 94, 43), new Color(245, 222, 179)),
        SLATE("Slate", new Color(216, 221, 228), new Color(94, 111, 132),
                new Color(74, 84, 96), new Color(245, 248, 250)),
        MIDNIGHT("Midnight", new Color(190, 204, 223), new Color(46, 63, 86),
                new Color(35, 47, 64), new Color(233, 239, 246));

        private final String displayName;
        private final Color lightSquare;
        private final Color darkSquare;
        private final Color lightLabel;
        private final Color darkLabel;

        BoardStyle(String displayName, Color lightSquare, Color darkSquare, Color lightLabel, Color darkLabel) {
            this.displayName = displayName;
            this.lightSquare = lightSquare;
            this.darkSquare = darkSquare;
            this.lightLabel = lightLabel;
            this.darkLabel = darkLabel;
        }

        public Color getLightSquare() {
            return lightSquare;
        }

        public Color getDarkSquare() {
            return darkSquare;
        }

        public Color getLightLabel() {
            return lightLabel;
        }

        public Color getDarkLabel() {
            return darkLabel;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static PieceStyle pieceStyle = PieceStyle.ALPHA;
    private static BoardStyle boardStyle = BoardStyle.CLASSIC;
    private static float volume = 0.65f;

    private GameSettings() {
    }

    public static PieceStyle getPieceStyle() {
        return pieceStyle;
    }

    public static void setPieceStyle(PieceStyle pieceStyle) {
        if (pieceStyle != null) {
            GameSettings.pieceStyle = pieceStyle;
        }
    }

    public static BoardStyle getBoardStyle() {
        return boardStyle;
    }

    public static void setBoardStyle(BoardStyle boardStyle) {
        if (boardStyle != null) {
            GameSettings.boardStyle = boardStyle;
        }
    }

    public static float getVolume() {
        return volume;
    }

    public static int getVolumePercent() {
        return Math.round(volume * 100f);
    }

    public static void setVolume(float volume) {
        GameSettings.volume = Math.max(0f, Math.min(1f, volume));
    }

    public static void setVolumePercent(int percent) {
        setVolume(percent / 100f);
    }
}