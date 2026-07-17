package com.interview.agent.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class KnowledgeExplainSkill implements Skill {

    private final ChatModel chatModel;

    @Override
    public String name() {
        return "knowledge_explain";
    }

    @Override
    public boolean matches(String userInput) {
        String s = userInput.toLowerCase(Locale.ROOT);
        return s.contains("讲解") || s.contains("解释一下") || s.contains("什么是") || s.contains("原理");
    }

    @Override
    public String handle(SkillSession session, String userInput) {
        String reply = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是面试知识讲解助手，用校招友好的方式讲清原理，并给一个追问示例。"),
                new UserMessage(userInput)
        ))).getResult().getOutput().getText();
        session.setStep(-1);
        return reply;
    }
}
