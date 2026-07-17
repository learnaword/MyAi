package com.interview.agent.agent;

import com.interview.agent.memory.ShortTermMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAgent {

    private final ChatModel chatModel;
    private final ShortTermMemory shortTermMemory;

    private static final String CHAT_AGENT_PROMPT = """
            你是 InterviewAgent 的聊天助手，面向准备 Java 技术面试的校招/初级开发者。
            职责：
            1. 回答技术问题、面试技巧咨询、介绍系统功能；
            2. 当用户表达想面试时，引导其使用「开始面试」流程（提供 JD 与简历），不要在闲聊里草草开始正式面试；
            3. 语气专业、鼓励、简洁。
            """;

    public String chat(String sessionKey, String userInput) {
        List<Message> history = shortTermMemory.getHistory(sessionKey);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(CHAT_AGENT_PROMPT));
        messages.addAll(history);
        messages.add(new UserMessage(userInput));

        ChatResponse response = chatModel.call(new Prompt(messages));
        String reply = response.getResult().getOutput().getText();
        shortTermMemory.append(sessionKey, new UserMessage(userInput));
        shortTermMemory.append(sessionKey, response.getResult().getOutput());
        return reply;
    }
}
