package com.interview.agent.skill;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SkillSession {
    private String skillName;
    private int step;
    private final Map<String, Object> attrs = new HashMap<>();
}
