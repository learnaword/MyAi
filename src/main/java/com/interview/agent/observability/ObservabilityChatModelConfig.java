package com.interview.agent.observability;

import com.interview.agent.config.AppConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.NonNull;

@Configuration
public class ObservabilityChatModelConfig {

    @Bean
    public static BeanPostProcessor tracedChatModelPostProcessor(
            ObjectProvider<AiTraceContext> traceContextProvider,
            ObjectProvider<TokenPricingService> pricingProvider,
            ObjectProvider<AppConfig> appConfigProvider) {
        return new TracingChatModelBeanPostProcessor(traceContextProvider, pricingProvider, appConfigProvider);
    }

    static final class TracingChatModelBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {
        private final ObjectProvider<AiTraceContext> traceContextProvider;
        private final ObjectProvider<TokenPricingService> pricingProvider;
        private final ObjectProvider<AppConfig> appConfigProvider;

        TracingChatModelBeanPostProcessor(
                ObjectProvider<AiTraceContext> traceContextProvider,
                ObjectProvider<TokenPricingService> pricingProvider,
                ObjectProvider<AppConfig> appConfigProvider) {
            this.traceContextProvider = traceContextProvider;
            this.pricingProvider = pricingProvider;
            this.appConfigProvider = appConfigProvider;
        }

        @Override
        public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName)
                throws BeansException {
            if (!(bean instanceof ChatModel) || bean instanceof TracedChatModel) {
                return bean;
            }
            return new TracedChatModel(
                    (ChatModel) bean, traceContextProvider, pricingProvider, appConfigProvider);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
