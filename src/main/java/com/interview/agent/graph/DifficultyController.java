package com.interview.agent.graph;

import com.interview.agent.model.Difficulty;
import org.springframework.stereotype.Component;

@Component
public class DifficultyController {

    public record State(Difficulty difficulty, int streak) {}

    public State onVerdict(Difficulty current, int streak, String verdict) {
        boolean good = "good".equalsIgnoreCase(verdict);
        boolean poor = "poor".equalsIgnoreCase(verdict);
        if (good) {
            int next = streak >= 0 ? streak + 1 : 1;
            if (next >= 2) {
                return new State(current.up(), 0);
            }
            return new State(current, next);
        }
        if (poor) {
            int next = streak <= 0 ? streak - 1 : -1;
            if (next <= -2) {
                return new State(current.down(), 0);
            }
            return new State(current, next);
        }
        return new State(current, 0);
    }
}
