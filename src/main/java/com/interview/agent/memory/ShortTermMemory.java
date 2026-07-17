package com.interview.agent.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShortTermMemory {

    public static final int MAX_HISTORY_SIZE = 20;

    private final Map<String, List<Message>> histories = new ConcurrentHashMap<>();

    public List<Message> getHistory(String sessionKey) {
        return new ArrayList<>(histories.getOrDefault(sessionKey, List.of()));
    }

    public void append(String sessionKey, Message message) {
        histories.compute(sessionKey, (k, list) -> {
            List<Message> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
            next.add(message);
            if (next.size() > MAX_HISTORY_SIZE) {
                return new ArrayList<>(next.subList(next.size() - MAX_HISTORY_SIZE, next.size()));
            }
            return next;
        });
    }

    public void clear(String sessionKey) {
        histories.remove(sessionKey);
    }
}
