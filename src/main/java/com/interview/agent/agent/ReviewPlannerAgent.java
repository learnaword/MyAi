package com.interview.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.model.EvaluationReport;
import com.interview.agent.model.JsonSupport;
import com.interview.agent.model.ReviewPlan;
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
public class ReviewPlannerAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public ReviewPlan plan(EvaluationReport report, List<String> historicalWeakTopics) {
        String prompt = """
                评估报告: %s
                历史薄弱点: %s
                输出 JSON：focusTopics[], resources[], practiceSuggestions[], summary
                （resources 可用公开学习资料/文档名，暂不调用 GitHub）
                """.formatted(report, historicalWeakTopics);
        String raw = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是复习规划 Agent。只输出 JSON。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();
        try {
            JsonNode node = JsonSupport.readTree(objectMapper, raw);
            return ReviewPlan.builder()
                    .focusTopics(stringList(node.get("focusTopics")))
                    .resources(stringList(node.get("resources")))
                    .practiceSuggestions(stringList(node.get("practiceSuggestions")))
                    .summary(node.path("summary").asText(""))
                    .build();
        } catch (Exception e) {
            return ReviewPlan.builder()
                    .focusTopics(report.getWeakTopics())
                    .resources(List.of("JavaGuide", "Spring 官方文档", "相关开源项目 README"))
                    .practiceSuggestions(List.of("针对薄弱点做一次专题模拟", "用自己的项目复述亮点"))
                    .summary("根据评估薄弱点制定复习计划。")
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
