package com.interview.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.model.InterviewTurn;
import com.interview.agent.model.JsonSupport;
import com.interview.agent.model.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InterviewerAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public String presentQuestion(Question question, int index, int total) {
        return "第 %d/%d 题（%s / %s）\n%s".formatted(
                index + 1, total, question.getType(), question.getDifficulty(), question.getContent());
    }

    public GradeResult grade(Question question, String answer) {
        String prompt = """
                题目: %s
                参考答案: %s
                候选人回答: %s
                输出 JSON：verdict(good|partial|poor), score(0-100), followUp(若 partial 则给一个追问，否则空字符串), comment
                """.formatted(question.getContent(), question.getReferenceAnswer(), answer);
        String raw = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是面试官 Agent。只输出 JSON。partial 表示部分正确需追问一次。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();
        try {
            JsonNode node = JsonSupport.readTree(objectMapper, raw);
            return new GradeResult(
                    node.path("verdict").asText("partial"),
                    node.path("score").asInt(60),
                    node.path("followUp").asText(""),
                    node.path("comment").asText("")
            );
        } catch (Exception e) {
            return new GradeResult("partial", 60, "能否再展开说明一下关键点？", "自动评分降级");
        }
    }

    public InterviewTurn buildTurn(Question q, String answer, GradeResult grade, String followUpAnswer) {
        return InterviewTurn.builder()
                .question(q)
                .answer(answer)
                .followUpQuestion(grade.followUp())
                .followUpAnswer(followUpAnswer)
                .verdict(grade.verdict())
                .score(grade.score())
                .build();
    }

    public record GradeResult(String verdict, int score, String followUp, String comment) {}
}
