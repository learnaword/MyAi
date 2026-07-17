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
public class QuickQuizSkill implements Skill {

    private final ChatModel chatModel;

    @Override
    public String name() {
        return "quick_quiz";
    }

    @Override
    public boolean matches(String userInput) {
        String s = userInput.toLowerCase(Locale.ROOT);
        return s.contains("快速测验") || s.contains("小测") || s.contains("quiz");
    }

    @Override
    public String handle(SkillSession session, String userInput) {
        if (session.getStep() == 0) {
            session.setStep(1);
            String q = chatModel.call(new Prompt(List.of(
                    new SystemMessage("出一道简短 Java 面试选择题或简答题，只输出题目。"),
                    new UserMessage("来一道快速测验题")
            ))).getResult().getOutput().getText();
            session.getAttrs().put("question", q);
            return "【快速测验】\n" + q + "\n\n请直接作答；回复「退出技能」可结束。";
        }
        if (userInput.contains("退出技能")) {
            session.setStep(-1);
            return "已退出快速测验。";
        }
        String q = String.valueOf(session.getAttrs().get("question"));
        String feedback = chatModel.call(new Prompt(List.of(
                new SystemMessage("点评候选人的快速测验回答，简洁给出对错与要点。"),
                new UserMessage("题目: " + q + "\n回答: " + userInput)
        ))).getResult().getOutput().getText();
        session.setStep(-1);
        return feedback + "\n\n（快速测验结束）";
    }
}
