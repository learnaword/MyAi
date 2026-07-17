package com.interview.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.model.EvaluationReport;
import com.interview.agent.model.InterviewTurn;
import com.interview.agent.model.JsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EvaluationAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public EvaluationReport evaluate(List<InterviewTurn> turns) {
        String prompt = "面试记录:\n" + turns + """
                
                输出 JSON：overallScore, technicalDepth, clarity, logic, projectDemo, summary, suggestions[], weakTopics[]
                """;
        String raw = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是评估 Agent。只输出 JSON。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();
        try {
            JsonNode node = JsonSupport.readTree(objectMapper, raw);
            return EvaluationReport.builder()
                    .overallScore(node.path("overallScore").asInt(60))
                    .technicalDepth(node.path("technicalDepth").asInt(60))
                    .clarity(node.path("clarity").asInt(60))
                    .logic(node.path("logic").asInt(60))
                    .projectDemo(node.path("projectDemo").asInt(60))
                    .summary(node.path("summary").asText(""))
                    .suggestions(stringList(node.get("suggestions")))
                    .weakTopics(stringList(node.get("weakTopics")))
                    .build();
        } catch (Exception e) {
            double avg = turns.stream().mapToInt(InterviewTurn::getScore).average().orElse(60);
            return EvaluationReport.builder()
                    .overallScore((int) avg)
                    .technicalDepth((int) avg)
                    .clarity((int) avg)
                    .logic((int) avg)
                    .projectDemo((int) avg)
                    .summary("评估降级：按题目均分汇总。")
                    .suggestions(List.of("针对低分题目回看参考答案并复述"))
                    .weakTopics(turns.stream()
                            .filter(t -> t.getScore() < 60)
                            .map(t -> t.getQuestion().getTopic())
                            .distinct()
                            .toList())
                    .build();
        }
    }

    private List<String> stringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
