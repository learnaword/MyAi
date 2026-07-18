package com.interview.agent.auth;

import java.util.regex.Pattern;

public final class PasswordRules {

    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    private PasswordRules() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw AuthException.badRequest("password must be at least 8 characters");
        }
        if (!HAS_LETTER.matcher(password).matches() || !HAS_DIGIT.matcher(password).matches()) {
            throw AuthException.badRequest("password must contain letters and digits");
        }
    }
}
