
package com.interview.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 处理器（与 Go 版本 ws_handler.go 完全一致的协议和逻辑）
 * - handleChat：3 级优先级（active skill → skill match → ChatAgent）
 * - handleStartInterview：创建 Orchestrator，异步运行面试
 * - handleAnswer：通过 answerCh 传递用户回答
 * - handleUploadQuestions：base64 解码 → SHA256 去重 → LLM 解析 → Milvus + BM25
 * - handleQuitInterview：用户主动终止
 */
@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
}
