package com.interview.agent.model;

public enum Difficulty {
    EASY, MEDIUM, HARD;

    public Difficulty up() {
        return switch (this) {
            case EASY -> MEDIUM;
            case MEDIUM, HARD -> HARD;
        };
    }

    public Difficulty down() {
        return switch (this) {
            case HARD -> MEDIUM;
            case MEDIUM, EASY -> EASY;
        };
    }

    public static Difficulty fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
