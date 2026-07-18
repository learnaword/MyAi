package com.interview.agent.skill;

import com.interview.agent.observability.AiTraceContext;
import com.interview.agent.observability.SpanType;
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
    private final AiTraceContext traceContext;
    private final Map<String, SkillSession> active = new ConcurrentHashMap<>();

    public Optional<String> route(String sessionKey, String userInput) {
        SkillSession existing = active.get(sessionKey);
        if (existing != null && existing.getStep() >= 0) {
            Skill skill = findByName(existing.getSkillName());
            if (skill != null) {
                return Optional.ofNullable(invokeSkill(skill, existing, userInput, sessionKey));
            }
            active.remove(sessionKey);
        }
        for (Skill skill : skills) {
            if (skill.matches(userInput)) {
                SkillSession session = new SkillSession();
                session.setSkillName(skill.name());
                session.setStep(0);
                String reply = invokeSkill(skill, session, userInput, sessionKey);
                if (session.getStep() >= 0) {
                    active.put(sessionKey, session);
                }
                return Optional.ofNullable(reply);
            }
        }
        return Optional.empty();
    }

    private String invokeSkill(Skill skill, SkillSession session, String userInput, String sessionKey) {
        String toolName = skill.getClass().getSimpleName();
        try (AiTraceContext.ActiveSpan span = traceContext.startSpan(SpanType.TOOL, "tool.skill." + toolName)) {
            span.draft().setToolName(toolName);
            traceContext.setAgentNode(toolName, "skill");
            try {
                String reply = skill.handle(session, userInput);
                if (session.getStep() < 0) {
                    active.remove(sessionKey);
                }
                if (reply == null || reply.isBlank()) {
                    span.error("EMPTY_REPLY", "skill returned empty reply");
                    return reply;
                }
                span.ok();
                return reply;
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            } finally {
                traceContext.setAgentNode(null, null);
            }
        }
    }

    private Skill findByName(String name) {
        return skills.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
    }
}
