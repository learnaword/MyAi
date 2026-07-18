package com.interview.agent.observability;

import com.interview.agent.config.AppConfig;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

public class TracedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ObjectProvider<AiTraceContext> traceContextProvider;
    private final ObjectProvider<TokenPricingService> pricingProvider;
    private final ObjectProvider<AppConfig> appConfigProvider;

    public TracedChatModel(
            ChatModel delegate,
            ObjectProvider<AiTraceContext> traceContextProvider,
            ObjectProvider<TokenPricingService> pricingProvider,
            ObjectProvider<AppConfig> appConfigProvider) {
        this.delegate = delegate;
        this.traceContextProvider = traceContextProvider;
        this.pricingProvider = pricingProvider;
        this.appConfigProvider = appConfigProvider;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        AppConfig appConfig = appConfigProvider.getIfAvailable();
        AiTraceContext traceContext = traceContextProvider.getIfAvailable();
        TokenPricingService pricingService = pricingProvider.getIfAvailable();
        if (appConfig == null || !appConfig.getObservability().isEnabled()
                || traceContext == null || pricingService == null
                || AiTraceContext.current() == null) {
            return delegate.call(prompt);
        }
        String name = resolveSpanName(traceContext);
        try (AiTraceContext.ActiveSpan span = traceContext.startSpan(SpanType.LLM, name)) {
            try {
                ChatResponse response = delegate.call(prompt);
                fillUsage(span, prompt, response, pricingService, appConfig);
                span.ok();
                return response;
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            }
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private String resolveSpanName(AiTraceContext traceContext) {
        String node = traceContext.node();
        if (node != null && node.contains("rerank")) {
            return "llm.rerank";
        }
        String agent = traceContext.agent();
        if (agent != null && agent.toLowerCase().contains("rerank")) {
            return "llm.rerank";
        }
        return "llm.chat";
    }

    private void fillUsage(
            AiTraceContext.ActiveSpan span,
            Prompt prompt,
            ChatResponse response,
            TokenPricingService pricingService,
            AppConfig appConfig) {
        AiSpanDraft draft = span.draft();
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        String model = null;
        if (metadata != null && metadata.getModel() != null && !metadata.getModel().isBlank()) {
            model = metadata.getModel();
        }
        if (model == null) {
            model = appConfig.getObservability().getDefaultModel();
        }
        draft.setModel(model);

        Usage usage = metadata == null ? null : metadata.getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        Integer totalTokens = usage == null ? null : usage.getTotalTokens();
        draft.setPromptTokens(promptTokens);
        draft.setCompletionTokens(completionTokens);
        draft.setTotalTokens(totalTokens);

        TokenPricingService.Cost cost = pricingService.estimate(model, promptTokens, completionTokens);
        draft.setCostAmount(cost.amount());
        draft.setCostCurrency(cost.currency());

        Map<String, Object> attrs = new HashMap<>();
        if (prompt != null && prompt.getInstructions() != null) {
            int chars = prompt.getInstructions().stream()
                    .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                    .sum();
            attrs.put("promptChars", chars);
        }
        if (response != null && response.getResult() != null
                && response.getResult().getOutput() != null
                && response.getResult().getOutput().getText() != null) {
            attrs.put("completionChars", response.getResult().getOutput().getText().length());
        }
        if (promptTokens == null) {
            attrs.put("usageMissing", true);
        }
        draft.setAttributes(attrs);
    }
}
