package com.interview.agent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.loader.QuestionBankLoader;
import com.interview.agent.model.*;
import com.interview.agent.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionPlannerAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final RagService ragService;

    public List<Question> plan(JdRequirement jd, MatchReport match, String resumeText, int maxQuestions,
                               List<String> weakTopics, Difficulty startDifficulty) {
        List<Direction> directions = planDirections(jd, match, resumeText, maxQuestions, weakTopics);
        List<Question> questions = new ArrayList<>();
        for (Direction d : directions) {
            String query = d.topic() + " " + d.type() + " " + String.join(" ", d.keywords());
            Optional<Question> bankHit = ragService.retrieveBest(query);
            if (bankHit.isPresent()) {
                Question q = bankHit.get();
                q.setTopic(d.topic());
                q.setType(d.type());
                q.setDifficulty(d.difficulty() != null ? d.difficulty() : startDifficulty);
                q.setSource(QuestionSource.BANK);
                questions.add(q);
            } else {
                questions.add(generate(d, startDifficulty));
            }
        }
        if (questions.isEmpty()) {
            questions.add(generate(new Direction("Java基础", "基础知识", List.of("集合", "并发"), startDifficulty), startDifficulty));
        }
        return questions;
    }

    private List<Direction> planDirections(JdRequirement jd, MatchReport match, String resumeText,
                                           int maxQuestions, List<String> weakTopics) {
        String prompt = """
                基于简历(主)和JD(辅)规划出题方向，数量=%d。
                输出 JSON：{"directions":[{"topic":"","type":"基础知识|项目经历|系统设计","difficulty":"EASY|MEDIUM|HARD","keywords":[]}]}
                JD=%s
                匹配=%s
                薄弱点=%s
                简历摘要=%s
                """.formatted(maxQuestions, jd, match, weakTopics,
                resumeText == null ? "" : resumeText.substring(0, Math.min(2000, resumeText.length())));
        try {
            String raw = chatModel.call(new Prompt(List.of(
                    new SystemMessage("你是出题规划 Agent。只输出 JSON。"),
                    new UserMessage(prompt)
            ))).getResult().getOutput().getText();
            JsonNode root = JsonSupport.readTree(objectMapper, raw);
            JsonNode arr = root.path("directions");
            List<Direction> list = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    List<String> kws = new ArrayList<>();
                    if (n.path("keywords").isArray()) {
                        n.path("keywords").forEach(x -> kws.add(x.asText()));
                    }
                    list.add(new Direction(
                            n.path("topic").asText("Java"),
                            n.path("type").asText("基础知识"),
                            kws,
                            Difficulty.fromString(n.path("difficulty").asText("MEDIUM"))
                    ));
                }
            }
            return list.stream().limit(maxQuestions).toList();
        } catch (Exception e) {
            log.warn("direction plan failed: {}", e.getMessage());
            return List.of(
                    new Direction("Java集合", "基础知识", List.of("HashMap"), Difficulty.MEDIUM),
                    new Direction("Spring", "基础知识", List.of("事务"), Difficulty.MEDIUM),
                    new Direction("项目经历", "项目经历", List.of("项目亮点"), Difficulty.MEDIUM)
            );
        }
    }

    private Question generate(Direction d, Difficulty fallback) {
        Difficulty diff = d.difficulty() != null ? d.difficulty() : fallback;
        String raw = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是面试出题器。只输出 JSON：content, referenceAnswer"),
                new UserMessage("请生成一道%s难度的%s面试题，主题：%s，关键词：%s"
                        .formatted(diff, d.type(), d.topic(), d.keywords()))
        ))).getResult().getOutput().getText();
        try {
            JsonNode node = JsonSupport.readTree(objectMapper, raw);
            String content = node.path("content").asText("请介绍一下 " + d.topic());
            return Question.builder()
                    .id(QuestionBankLoader.sha256(content))
                    .topic(d.topic())
                    .type(d.type())
                    .difficulty(diff)
                    .content(content)
                    .referenceAnswer(node.path("referenceAnswer").asText(""))
                    .source(QuestionSource.GENERATED)
                    .build();
        } catch (Exception e) {
            String content = "请结合你的经历，谈谈对「" + d.topic() + "」的理解。";
            return Question.builder()
                    .id(QuestionBankLoader.sha256(content))
                    .topic(d.topic())
                    .type(d.type())
                    .difficulty(diff)
                    .content(content)
                    .referenceAnswer("")
                    .source(QuestionSource.GENERATED)
                    .build();
        }
    }

    private record Direction(String topic, String type, List<String> keywords, Difficulty difficulty) {}
}
