package com.interview.agent.observability.query;

public class ObservabilityBadRequestException extends RuntimeException {
    public ObservabilityBadRequestException(String message) {
        super(message);
    }
}
