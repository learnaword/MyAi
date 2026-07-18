package com.interview.agent.observability.query;

public class ObservabilityNotFoundException extends RuntimeException {
    public ObservabilityNotFoundException(String message) {
        super(message);
    }
}
