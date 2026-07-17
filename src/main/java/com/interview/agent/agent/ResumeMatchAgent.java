package com.interview.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.model.JdRequirement;
import com.interview.agent.model.JsonSupport;
import com.interview.agent.model.MatchReport;
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
public class ResumeMatchAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public MatchReport match(JdRequirement jd, String resumeText) {
        String prompt = """
                JD: %s
                简历: %s
                请输出 JSON：score(0-100), strengths[], gaps[], summary
                """.formatted(jd, resumeText);
        String raw = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是简历匹配 Agent。只输出 JSON。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();
        try {
            JsonNode node = JsonSupport.readTree(objectMapper, raw);
            return MatchReport.builder()
                    .score(node.path("score").asInt(60))
                    .strengths(stringList(node.get("strengths")))
                    .gaps(stringList(node.get("gaps")))
                    .summary(node.path("summary").asText(""))
                    .build();
        } catch (Exception e) {
            return MatchReport.builder()
                    .score(60)
                    .strengths(List.of())
                    .gaps(List.of())
                    .summary("匹配分析失败，将按通用策略出题。")
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
