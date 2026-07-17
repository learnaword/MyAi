package com.interview.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.model.JdRequirement;
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
public class JdAnalysisAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public JdRequirement analyze(String jdText) {
        String raw = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是 JD 分析 Agent。只输出 JSON：title, techStack[], requiredSkills[], experience, summary"),
                new UserMessage("请解析以下岗位 JD：\n" + jdText)
        ))).getResult().getOutput().getText();
        try {
            JsonNode node = JsonSupport.readTree(objectMapper, raw);
            return JdRequirement.builder()
                    .title(text(node, "title"))
                    .techStack(stringList(node.get("techStack")))
                    .requiredSkills(stringList(node.get("requiredSkills")))
                    .experience(text(node, "experience"))
                    .summary(text(node, "summary"))
                    .build();
        } catch (Exception e) {
            return JdRequirement.builder()
                    .title("未命名岗位")
                    .summary(jdText.length() > 300 ? jdText.substring(0, 300) : jdText)
                    .techStack(List.of())
                    .requiredSkills(List.of())
                    .build();
        }
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText("") : "";
    }

    private List<String> stringList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
