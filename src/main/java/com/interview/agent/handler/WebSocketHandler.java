package com.interview.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.agent.ChatAgent;
import com.interview.agent.config.AppConfig;
import com.interview.agent.config.LlmErrorMessages;
import com.interview.agent.graph.InterviewOrchestrator;
import com.interview.agent.graph.InterviewStateKeys;
import com.interview.agent.loader.JdLoader;
import com.interview.agent.loader.QuestionBankLoader;
import com.interview.agent.loader.ResumeLoader;
import com.interview.agent.model.Question;
import com.interview.agent.model.WsInboundMessage;
import com.interview.agent.model.WsOutboundMessage;
import com.interview.agent.model.entity.InterviewSessionEntity;
import com.interview.agent.observability.AiTraceContext;
import com.interview.agent.observability.AiTraceScope;
import com.interview.agent.observability.ObsScene;
import com.interview.agent.observability.SpanStatus;
import com.interview.agent.rag.RagService;
import com.interview.agent.repository.InterviewSessionRepository;
import com.interview.agent.skill.SkillRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatAgent chatAgent;
    private final SkillRouter skillRouter;
    private final InterviewOrchestrator interviewOrchestrator;
    private final JdLoader jdLoader;
    private final ResumeLoader resumeLoader;
    private final QuestionBankLoader questionBankLoader;
    private final RagService ragService;
    private final InterviewSessionRepository interviewSessionRepository;
    private final AiTraceContext traceContext;
    private final AppConfig appConfig;

    private final ExecutorService interviewExecutor = Executors.newCachedThreadPool();
    private final Map<String, String> activeInterviewByWs = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[WS] connected {}", session.getId());
        send(session, WsOutboundMessage.of("system", "已连接 InterviewAgent。可聊天、上传题库，或发送 start_interview 开始面试。"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            WsInboundMessage inbound = objectMapper.readValue(message.getPayload(), WsInboundMessage.class);
            String type = inbound.getType() == null ? "" : inbound.getType().trim().toLowerCase();
            switch (type) {
                case "chat" -> handleChat(session, inbound);
                case "start_interview", "start" -> handleStartInterview(session, inbound);
                case "answer" -> handleAnswer(session, inbound);
                case "upload_questions" -> handleUploadQuestions(session, inbound);
                case "quit", "quit_interview" -> handleQuit(session);
                default -> send(session, withTrace(WsOutboundMessage.error("未知消息类型: " + type)));
            }
        } catch (Exception e) {
            log.error("[WS] handle error", e);
            send(session, withTrace(WsOutboundMessage.error(e.getMessage())));
        }
    }

    private void handleChat(WebSocketSession session, WsInboundMessage inbound) {
        String content = inbound.getContent() == null ? "" : inbound.getContent().trim();
        if (content.isBlank()) {
            send(session, withTrace(WsOutboundMessage.error("聊天内容不能为空")));
            return;
        }
        ObsScene scene = ObsScene.CHAT;
        AiTraceScope scope = traceContext.openRootNamed(scene, "chat", session.getId(), null, null);
        try {
            String key = session.getId();
            var skillReply = skillRouter.route(key, content);
            String reply;
            if (skillReply.isPresent()) {
                if (scope != null) {
                    // reopen conceptually as SKILL — keep same trace, update scene on entity is skip; attrs enough
                    scope.setScene(ObsScene.SKILL);
                }
                reply = skillReply.get();
            } else {
                traceContext.setAgentNode("ChatAgent", "chat");
                try (var span = traceContext.startSpan(
                        com.interview.agent.observability.SpanType.AGENT, "agent.chat")) {
                    span.draft().setAgent("ChatAgent");
                    span.draft().setNode("chat");
                    try {
                        reply = chatAgent.chat(key, content);
                        span.ok();
                    } catch (RuntimeException e) {
                        span.error(e);
                        throw e;
                    }
                } finally {
                    traceContext.setAgentNode(null, null);
                }
            }
            send(session, withTrace(WsOutboundMessage.of("chat", reply)));
            traceContext.closeRoot(SpanStatus.OK, null);
        } catch (Exception e) {
            send(session, withTrace(WsOutboundMessage.error(e.getMessage())));
            traceContext.closeRoot(SpanStatus.ERROR, e.getMessage());
        }
    }

    private void handleStartInterview(WebSocketSession session, WsInboundMessage inbound) {
        if (activeInterviewByWs.containsKey(session.getId())) {
            send(session, withTrace(WsOutboundMessage.error("当前已有进行中的面试，请先结束或退出")));
            return;
        }
        String jdText;
        String resumeText;
        try {
            jdText = jdLoader.load(inbound.getJd(), inbound.getJdUrl());
            if (inbound.getResumeText() != null && !inbound.getResumeText().isBlank()) {
                resumeText = inbound.getResumeText();
            } else if (inbound.getResumeBase64() != null && !inbound.getResumeBase64().isBlank()) {
                resumeText = resumeLoader.loadFromBase64(inbound.getResumeFilename(), inbound.getResumeBase64());
            } else {
                throw new IllegalArgumentException("请提供简历文本或简历文件");
            }
        } catch (Exception e) {
            send(session, withTrace(WsOutboundMessage.error(e.getMessage())));
            return;
        }

        String interviewSessionId = UUID.randomUUID().toString().replace("-", "");
        activeInterviewByWs.put(session.getId(), interviewSessionId);

        AiTraceScope scope = traceContext.openRootNamed(
                ObsScene.INTERVIEW, "start_interview", session.getId(), interviewSessionId, null);
        String traceId = scope == null ? null : scope.getTraceId();

        InterviewSessionEntity entity = InterviewSessionEntity.builder()
                .id(interviewSessionId)
                .status("RUNNING")
                .jdText(jdText)
                .resumeText(resumeText)
                .build();
        interviewSessionRepository.save(entity);

        interviewOrchestrator.registerSink(interviewSessionId, (type, content, data) ->
                send(session, withTrace(WsOutboundMessage.builder()
                        .type(type)
                        .content(content)
                        .data(data)
                        .sessionId(interviewSessionId)
                        .traceId(traceId)
                        .build(), false)));

        send(session, withTrace(WsOutboundMessage.builder()
                .type("interview_started")
                .content("面试已开始")
                .sessionId(interviewSessionId)
                .traceId(traceId)
                .build(), false));

        // clear WS thread binding; interview thread will bind by session
        traceContext.clear();

        interviewExecutor.submit(() -> {
            AiTraceScope interviewScope = traceContext.scopeForSession(interviewSessionId);
            try {
                if (interviewScope != null) {
                    traceContext.bind(interviewScope);
                }
                var state = interviewOrchestrator.run(interviewSessionId, null, jdText, resumeText);
                entity.setStatus("FINISHED");
                entity.setFinishedAt(Instant.now());
                Object evaluation = state.value(InterviewStateKeys.EVALUATION, null);
                Object review = state.value(InterviewStateKeys.REVIEW_PLAN, null);
                if (evaluation != null) {
                    entity.setEvaluationJson(objectMapper.writeValueAsString(evaluation));
                }
                if (review != null) {
                    entity.setReviewPlanJson(objectMapper.writeValueAsString(review));
                }
                Object match = state.value(InterviewStateKeys.MATCH_REPORT, null);
                if (match != null) {
                    entity.setMatchReportJson(objectMapper.writeValueAsString(match));
                }
                interviewSessionRepository.save(entity);
                if (interviewScope != null) {
                    traceContext.bind(interviewScope);
                    traceContext.closeRoot(SpanStatus.OK, null);
                }
            } catch (Exception e) {
                log.error("[Interview] failed {}", interviewSessionId, e);
                entity.setStatus("FAILED");
                interviewSessionRepository.save(entity);
                send(session, withTrace(WsOutboundMessage.error("面试执行失败: " + LlmErrorMessages.friendly(e))
                        .toBuilder().traceId(traceId).build(), false));
                if (interviewScope != null) {
                    traceContext.bind(interviewScope);
                    traceContext.closeRoot(SpanStatus.ERROR, e.getMessage());
                }
            } finally {
                interviewOrchestrator.unregister(interviewSessionId);
                activeInterviewByWs.remove(session.getId(), interviewSessionId);
                traceContext.unregisterSession(interviewSessionId);
                traceContext.clear();
            }
        });
    }

    private void handleAnswer(WebSocketSession session, WsInboundMessage inbound) {
        String interviewId = activeInterviewByWs.get(session.getId());
        if (inbound.getSessionId() != null && !inbound.getSessionId().isBlank()) {
            interviewId = inbound.getSessionId();
        }
        if (interviewId == null) {
            send(session, withTrace(WsOutboundMessage.error("没有进行中的面试")));
            return;
        }
        String answer = inbound.getAnswer() != null ? inbound.getAnswer() : inbound.getContent();
        if (answer == null || answer.isBlank()) {
            send(session, withTrace(WsOutboundMessage.error("回答不能为空")));
            return;
        }
        AiTraceScope scope = traceContext.scopeForSession(interviewId);
        try {
            if (scope != null) {
                traceContext.bind(scope);
            }
            interviewOrchestrator.submitAnswer(interviewId, answer);
            send(session, withTrace(WsOutboundMessage.builder()
                    .type("answer_received")
                    .content("已收到回答")
                    .sessionId(interviewId)
                    .build()));
        } finally {
            traceContext.clear();
        }
    }

    private void handleUploadQuestions(WebSocketSession session, WsInboundMessage inbound) {
        AiTraceScope scope = traceContext.openRootNamed(ObsScene.UPLOAD, "upload_questions", session.getId(), null, null);
        try {
            List<Question> questions = questionBankLoader.loadFromBase64(
                    inbound.getFilename(), inbound.getFileBase64());
            ragService.upsertBank(questions);
            send(session, withTrace(WsOutboundMessage.builder()
                    .type("upload_result")
                    .content("题库上传成功，共 " + questions.size() + " 题（当前库大小 " + ragService.bankSize() + "）")
                    .data(Map.of("count", questions.size(), "bankSize", ragService.bankSize()))
                    .build()));
            traceContext.closeRoot(SpanStatus.OK, null);
        } catch (Exception e) {
            send(session, withTrace(WsOutboundMessage.error("题库上传失败: " + e.getMessage())));
            traceContext.closeRoot(SpanStatus.ERROR, e.getMessage());
        }
    }

    private void handleQuit(WebSocketSession session) {
        String interviewId = activeInterviewByWs.get(session.getId());
        if (interviewId == null) {
            send(session, withTrace(WsOutboundMessage.of("system", "当前没有进行中的面试")));
            return;
        }
        AiTraceScope scope = traceContext.scopeForSession(interviewId);
        try {
            if (scope != null) {
                traceContext.bind(scope);
            }
            interviewOrchestrator.requestQuit(interviewId);
            send(session, withTrace(WsOutboundMessage.builder()
                    .type("quit")
                    .content("已请求结束面试")
                    .sessionId(interviewId)
                    .build()));
        } finally {
            traceContext.clear();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String interviewId = activeInterviewByWs.remove(session.getId());
        if (interviewId != null) {
            interviewOrchestrator.requestQuit(interviewId);
            interviewOrchestrator.unregister(interviewId);
            AiTraceScope scope = traceContext.scopeForSession(interviewId);
            if (scope != null) {
                traceContext.bind(scope);
                traceContext.closeRoot(SpanStatus.ERROR, "CANCELLED:ws_closed");
            }
            traceContext.unregisterSession(interviewId);
        }
    }

    private WsOutboundMessage withTrace(WsOutboundMessage payload) {
        return withTrace(payload, true);
    }

    private WsOutboundMessage withTrace(WsOutboundMessage payload, boolean fillFromContext) {
        if (payload == null || !appConfig.getObservability().isEnabled()) {
            return payload;
        }
        if (fillFromContext && payload.getTraceId() == null && shouldAttach(payload.getType())) {
            payload.setTraceId(AiTraceContext.currentTraceId());
        }
        if (!fillFromContext && payload.getTraceId() != null && !shouldAttach(payload.getType())) {
            payload.setTraceId(null);
        }
        return payload;
    }

    private boolean shouldAttach(String type) {
        if (type == null) {
            return false;
        }
        String configured = appConfig.getObservability().getAttachTraceIdTypes();
        if (configured == null || configured.isBlank() || "*".equals(configured.trim())) {
            return true;
        }
        Set<String> allowed = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return allowed.contains(type);
    }

    private void send(WebSocketSession session, WsOutboundMessage payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (IOException e) {
            log.warn("[WS] send failed: {}", e.getMessage());
        }
    }
}
