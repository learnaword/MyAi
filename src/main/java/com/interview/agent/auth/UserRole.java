package com.interview.agent.auth;

public enum UserRole {
    USER,
    ADMIN;

    public static UserRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            return USER;
        }
        return UserRole.valueOf(raw.trim().toUpperCase());
    }
}
