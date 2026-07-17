package com.interview.agent.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultyControllerTest {

    private final DifficultyController controller = new DifficultyController();

    @Test
    void upgradesAfterTwoGoods() {
        var s1 = controller.onVerdict(com.interview.agent.model.Difficulty.EASY, 0, "good");
        var s2 = controller.onVerdict(s1.difficulty(), s1.streak(), "good");
        assertEquals(com.interview.agent.model.Difficulty.MEDIUM, s2.difficulty());
        assertEquals(0, s2.streak());
    }

    @Test
    void downgradesAfterTwoPoors() {
        var s1 = controller.onVerdict(com.interview.agent.model.Difficulty.HARD, 0, "poor");
        var s2 = controller.onVerdict(s1.difficulty(), s1.streak(), "poor");
        assertEquals(com.interview.agent.model.Difficulty.MEDIUM, s2.difficulty());
    }
}
