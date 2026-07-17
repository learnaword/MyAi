package com.interview.agent.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.milvus", name = "enabled", havingValue = "true")
public class MilvusConfig {

    private final AppConfig appConfig;

    @Bean
    public MilvusClientV2 milvusClient() {
        String uri = String.format("http://%s:%d",
                appConfig.getMilvus().getHost(),
                appConfig.getMilvus().getPort());
        log.info("[Milvus] 连接: {}", uri);
        return new MilvusClientV2(ConnectConfig.builder().uri(uri).build());
    }
}
