package com.interview.agent.rag;

import com.interview.agent.model.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmReranker {

    private final ChatModel chatModel;

    public List<Question> rerank(String query, List<Question> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() <= topN) {
            return candidates;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("查询方向: ").append(query).append("\n候选题目:\n");
            for (int i = 0; i < candidates.size(); i++) {
                sb.append(i).append(". ").append(candidates.get(i).getContent()).append("\n");
            }
            sb.append("请按相关性从高到低返回题目序号，逗号分隔，只返回数字。");
            String raw = chatModel.call(new Prompt(List.of(
                    new SystemMessage("你是检索重排序器，只输出序号列表。"),
                    new UserMessage(sb.toString())
            ))).getResult().getOutput().getText();
            List<Question> ordered = new ArrayList<>();
            for (String part : raw.replaceAll("[^0-9,]", ",").split(",")) {
                if (part.isBlank()) continue;
                int idx = Integer.parseInt(part.trim());
                if (idx >= 0 && idx < candidates.size()) {
                    Question q = candidates.get(idx);
                    if (!ordered.contains(q)) {
                        ordered.add(q);
                    }
                }
                if (ordered.size() >= topN) break;
            }
            if (!ordered.isEmpty()) {
                return ordered;
            }
        } catch (Exception e) {
            log.warn("[Rerank] fallback to original order: {}", e.getMessage());
        }
        return candidates.stream().limit(topN).toList();
    }
}
