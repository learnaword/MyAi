package com.interview.agent.loader;

import com.interview.agent.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class QuestionBankLoaderTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private ResumeLoader resumeLoader;

    private QuestionBankLoader loader;

    @BeforeEach
    void setUp() {
        loader = new QuestionBankLoader(chatModel, new ObjectMapper(), resumeLoader);
    }

    @Test
    void parsesQaPairsWithoutLlm() {
        String text = """
                题：什么是 HashMap？
                答：基于数组+链表/红黑树的 Map 实现。

                题：Spring 事务传播行为有哪些？
                答：REQUIRED、REQUIRES_NEW 等。
                """;
        List<Question> questions = loader.loadFromBase64("bank.md",
                Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, questions.size());
        assertFalse(questions.get(0).getContent().isBlank());
        verifyNoInteractions(chatModel);
    }

    @Test
    void parsesJsonArrayWithoutLlm() {
        String text = """
                [
                  {"content":"解释 GC","referenceAnswer":"答案","topic":"JVM","difficulty":"MEDIUM"}
                ]
                """;
        List<Question> questions = loader.loadFromBase64("bank.json",
                Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, questions.size());
        assertEquals("解释 GC", questions.get(0).getContent());
        verifyNoInteractions(chatModel);
    }
}
