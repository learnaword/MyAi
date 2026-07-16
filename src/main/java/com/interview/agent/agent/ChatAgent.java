
package com.interview.agent.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAgent {

    private final ChatModel chatModel;

    /** 聊天历史上限：20 条消息（10 轮对话），与 Go 版本一致 */
    private static final int MAX_HISTORY_SIZE = 20;

    private static final String CHAT_AGENT_PROMPT = "";

    /**
     * 聊天：维护历史上下文，限制最近 20 条消息
     */
    public String chat(List<Message> history, String userInput) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(CHAT_AGENT_PROMPT));

        // 截取最近 20 条历史消息
        if (history != null && !history.isEmpty()) {
            int startIdx = Math.max(0, history.size() - MAX_HISTORY_SIZE);
            messages.addAll(history.subList(startIdx, history.size()));
        }

        messages.add(new UserMessage(userInput));

        ChatResponse response = chatModel.call(new Prompt(messages));
        return response.getResult().getOutput().getText();
    }
}
