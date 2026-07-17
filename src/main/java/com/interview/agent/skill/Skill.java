package com.interview.agent.skill;

import java.util.Optional;

public interface Skill {
    String name();
    boolean matches(String userInput);
    /** @return reply; empty means skill ended and caller should fall through */
    String handle(SkillSession session, String userInput);
}
