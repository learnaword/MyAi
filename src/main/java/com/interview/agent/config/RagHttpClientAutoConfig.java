package com.interview.agent.config;

import org.eclipse.jetty.client.HttpClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JettyClientHttpRequestFactory;

import java.time.Duration;

/**
 * 放大 DashScope 同步 HTTP 调用的超时。
 *
 * <p>Spring AI Alibaba 1.1.2.0 使用 Jetty {@code HttpClient}，
 * {@link JettyClientHttpRequestFactory} 的 readTimeout 会映射为 Jetty
 * {@code request.timeout(...)}（即 Total timeout），默认仅 10 秒。
 * 简历匹配/出题等长提示很容易超时重试。
 */
@Configuration
public class RagHttpClientAutoConfig {

    /** 总超时（秒），可用环境变量 DASHSCOPE_READ_TIMEOUT 覆盖（默认 300）。 */
    private static final long TIMEOUT_SECONDS =
            Long.parseLong(System.getenv().getOrDefault("DASHSCOPE_READ_TIMEOUT", "300"));

    @Bean(destroyMethod = "stop")
    public HttpClient dashScopeJettyHttpClient() throws Exception {
        HttpClient client = new HttpClient();
        // token 流式间隔也可能较长，一并放大空闲超时
        client.setIdleTimeout(TIMEOUT_SECONDS * 1000L);
        client.setConnectTimeout(30_000L);
        client.start();
        return client;
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public RestClientCustomizer dashScopeLargeTimeoutRestClientCustomizer(HttpClient dashScopeJettyHttpClient) {
        JettyClientHttpRequestFactory factory = new JettyClientHttpRequestFactory(dashScopeJettyHttpClient);
        // Spring 会把该值设为 Jetty request total timeout（报错里的 Total timeout）
        factory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        factory.setConnectTimeout(Duration.ofSeconds(30));
        return builder -> builder.requestFactory(factory);
    }
}
