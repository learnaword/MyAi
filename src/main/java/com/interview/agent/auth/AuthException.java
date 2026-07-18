package com.interview.agent.auth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public AuthException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static AuthException badRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static AuthException conflict(String message) {
        return new AuthException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static AuthException unauthorized(String error, String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED, error, message);
    }

    public static AuthException forbidden(String message) {
        return new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static AuthException notFound(String error, String message) {
        return new AuthException(HttpStatus.NOT_FOUND, error, message);
    }

    public static AuthException tooMany(String message) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", message);
    }

    public static AuthException mailNotConfigured() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_NOT_CONFIGURED", "mail is not configured");
    }
}
