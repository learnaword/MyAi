package com.interview.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SkillRouter {

    private final List<Skill> skills;
    private final Map<String, SkillSession> active = new ConcurrentHashMap<>();

    public Optional<String> route(String sessionKey, String userInput) {
        SkillSession existing = active.get(sessionKey);
        if (existing != null && existing.getStep() >= 0) {
            Skill skill = findByName(existing.getSkillName());
            if (skill != null) {
                String reply = skill.handle(existing, userInput);
                if (existing.getStep() < 0) {
                    active.remove(sessionKey);
                }
                return Optional.ofNullable(reply);
            }
            active.remove(sessionKey);
        }
        for (Skill skill : skills) {
            if (skill.matches(userInput)) {
                SkillSession session = new SkillSession();
                session.setSkillName(skill.name());
                session.setStep(0);
                String reply = skill.handle(session, userInput);
                if (session.getStep() >= 0) {
                    active.put(sessionKey, session);
                }
                return Optional.ofNullable(reply);
            }
        }
        return Optional.empty();
    }

    private Skill findByName(String name) {
        return skills.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
    }
}
