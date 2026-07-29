package com.jchess.util;

public enum EngineDifficulty {
    EASY("Easy", 1, 300),
    MEDIUM("Medium", 6, 600),
    HARD("Hard", 12, 1000),
    MASTER("Master", 20, 1500);

    private final String displayName;
    private final int skillLevel;
    private final int moveTimeMs;

    EngineDifficulty(String displayName, int skillLevel, int moveTimeMs) {
        this.displayName = displayName;
        this.skillLevel = skillLevel;
        this.moveTimeMs = moveTimeMs;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    public int getMoveTimeMs() {
        return moveTimeMs;
    }

    public static EngineDifficulty fromDisplayName(String name) {
        for (EngineDifficulty d : values()) {
            if (d.displayName.equalsIgnoreCase(name)) {
                return d;
            }
        }
        return MEDIUM; // default fallback
    }

    @Override
    public String toString() {
        return displayName;
    }
}
