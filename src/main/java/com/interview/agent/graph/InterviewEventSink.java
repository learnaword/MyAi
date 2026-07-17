package com.interview.agent.graph;

@FunctionalInterface
public interface InterviewEventSink {
    void emit(String type, String content, Object data);
}
