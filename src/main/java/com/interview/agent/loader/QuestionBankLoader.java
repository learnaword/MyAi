package com.interview.agent.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.model.Difficulty;
import com.interview.agent.model.Question;
import com.interview.agent.model.QuestionSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionBankLoader {

    private static final Pattern QA_PAIR = Pattern.compile(
            "(?is)(?:^|\\n)\\s*(?:#{1,3}\\s*)?(?:Q\\d*|题\\s*目?|问题)\\s*[.、.:：)\\-]\\s*(.+?)"
                    + "\\n\\s*(?:#{1,3}\\s*)?(?:A\\d*|答\\s*案?)\\s*[.、.:：)\\-]\\s*(.+?)(?="
                    + "\\n\\s*(?:#{1,3}\\s*)?(?:Q\\d*|题\\s*目?|问题)\\s*[.、.:：)\\-]|\\z)");

    private static final Pattern NUMBERED = Pattern.compile(
            "(?m)^\\s*(\\d+)[.、)\\]]\\s*(.+?)(?=^\\s*\\d+[.、)\\]]\\s*|\\z)",
            Pattern.DOTALL);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResumeLoader resumeLoader;

    public List<Question> loadFromBase64(String filename, String base64) {
        if (!StringUtils.hasText(base64)) {
            throw new IllegalArgumentException("题库内容为空");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("题库 base64 解码失败", e);
        }

        String lower = filename == null ? "" : filename.toLowerCase();
        String text;
        try {
            if (lower.endsWith(".pdf")) {
                text = resumeLoader.extractPdf(bytes);
            } else if (lower.endsWith(".docx")) {
                text = resumeLoader.extractDocx(bytes);
            } else {
                text = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("题库文件解析失败: " + e.getMessage(), e);
        }

        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("题库文件没有可识别的文本内容");
        }

        List<Question> parsed = parseLocally(text);
        if (!parsed.isEmpty()) {
            log.info("[QuestionBank] local parse ok, count={}, file={}", parsed.size(), filename);
            return parsed;
        }

        log.info("[QuestionBank] local parse empty, fallback to LLM, file={}", filename);
        return parseWithLlm(text);
    }

    private List<Question> parseLocally(String text) {
        List<Question> fromJson = parseJson(text);
        if (!fromJson.isEmpty()) {
            return fromJson;
        }
        List<Question> fromPairs = parseQaPairs(text);
        if (!fromPairs.isEmpty()) {
            return fromPairs;
        }
        return parseNumbered(text);
    }

    private List<Question> parseJson(String text) {
        String json = extractJsonArray(text.trim());
        if (!json.startsWith("[")) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {});
            return toQuestions(rows);
        } catch (Exception e) {
            log.debug("[QuestionBank] json parse skip: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Question> parseQaPairs(String text) {
        Matcher matcher = QA_PAIR.matcher(text);
        List<Question> result = new ArrayList<>();
        while (matcher.find()) {
            String content = clean(matcher.group(1));
            String answer = clean(matcher.group(2));
            if (!content.isBlank()) {
                result.add(bankQuestion(content, answer, "general"));
            }
        }
        return result;
    }

    private List<Question> parseNumbered(String text) {
        Matcher matcher = NUMBERED.matcher(text);
        List<Question> result = new ArrayList<>();
        while (matcher.find()) {
            String block = clean(matcher.group(2));
            if (block.length() < 8) {
                continue;
            }
            String content = block;
            String answer = "";
            Matcher ans = Pattern.compile("(?is)(?:答案|参考答案|解答)\\s*[.、.:：)\\-]?\\s*(.+)").matcher(block);
            if (ans.find()) {
                content = clean(block.substring(0, ans.start()));
                answer = clean(ans.group(1));
            }
            if (!content.isBlank()) {
                result.add(bankQuestion(content, answer, "general"));
            }
        }
        return result;
    }

    private List<Question> parseWithLlm(String text) {
        String clipped = text.length() > 6000 ? text.substring(0, 6000) : text;
        String prompt = """
                请从下面的面试题材料中提取题目，返回 JSON 数组，每项字段：
                topic, type, difficulty(EASY|MEDIUM|HARD), content, referenceAnswer
                只返回 JSON 数组，不要 markdown 代码块。

                材料：
                %s
                """.formatted(clipped);
        String raw;
        try {
            raw = chatModel.call(new Prompt(List.of(
                    new SystemMessage("你是面试题库解析器，只输出合法 JSON 数组。"),
                    new UserMessage(prompt)
            ))).getResult().getOutput().getText();
        } catch (RestClientException e) {
            throw wrapDashScopeError(e);
        } catch (RuntimeException e) {
            throw wrapDashScopeError(e);
        }

        String json = extractJsonArray(raw);
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {});
            List<Question> questions = toQuestions(rows);
            if (questions.isEmpty()) {
                throw new IllegalArgumentException("LLM 未提取到有效题目，请改用「题/答」或 JSON 数组格式上传");
            }
            return questions;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "LLM 返回无法解析。请改用本地格式上传（题/答成对，或 JSON 数组）。原始错误: " + e.getMessage(), e);
        }
    }

    private IllegalArgumentException wrapDashScopeError(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String hint = """
                DashScope 调用失败，无法用 LLM 解析题库。
                常见原因：DASHSCOPE_API_KEY 无效/过期、模型无权限、网络超时、返回了错误 JSON。
                建议：1) 检查 .env 中 DASHSCOPE_API_KEY；2) 改用本地可解析格式（见下方）；3) 换小一点的 txt/md。
                本地格式示例：
                题：什么是 HashMap？
                答：基于哈希表的 Map 实现…
                或 JSON：[{"content":"题目","referenceAnswer":"答案"}]
                原始错误: %s
                """.formatted(trimMsg(msg));
        log.error("[QuestionBank] DashScope parse failed: {}", msg);
        return new IllegalArgumentException(hint, e);
    }

    private List<Question> toQuestions(List<Map<String, Object>> rows) {
        List<Question> questions = new ArrayList<>();
        if (rows == null) {
            return questions;
        }
        for (Map<String, Object> row : rows) {
            String content = firstText(row, "content", "question", "q", "题目");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String answer = firstText(row, "referenceAnswer", "answer", "a", "答案");
            String topic = firstText(row, "topic", "标签", "category");
            if (!StringUtils.hasText(topic)) {
                topic = "general";
            }
            String type = firstText(row, "type", "题型");
            Difficulty difficulty = Difficulty.fromString(firstText(row, "difficulty", "难度"));
            questions.add(Question.builder()
                    .id(sha256(content))
                    .topic(topic)
                    .type(StringUtils.hasText(type) ? type : "基础知识")
                    .difficulty(difficulty)
                    .content(content.trim())
                    .referenceAnswer(answer == null ? "" : answer.trim())
                    .source(QuestionSource.BANK)
                    .build());
        }
        return questions;
    }

    private Question bankQuestion(String content, String answer, String topic) {
        return Question.builder()
                .id(sha256(content))
                .topic(topic)
                .type("基础知识")
                .difficulty(Difficulty.MEDIUM)
                .content(content)
                .referenceAnswer(answer == null ? "" : answer)
                .source(QuestionSource.BANK)
                .build();
    }

    private String firstText(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object val = row.get(key);
            if (val != null && StringUtils.hasText(String.valueOf(val))
                    && !"null".equalsIgnoreCase(String.valueOf(val))) {
                return String.valueOf(val);
            }
        }
        return "";
    }

    private String extractJsonArray(String raw) {
        if (raw == null) {
            return "[]";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        // single object -> wrap
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.isObject()) {
                    return "[" + trimmed + "]";
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return trimmed;
    }

    private String clean(String s) {
        return s == null ? "" : s.replaceAll("[ \\t]+\\n", "\n").trim();
    }

    private String trimMsg(String msg) {
        if (msg.length() <= 300) {
            return msg;
        }
        return msg.substring(0, 300) + "...";
    }

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
