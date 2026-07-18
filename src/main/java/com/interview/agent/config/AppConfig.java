
package com.interview.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private MilvusProperties milvus = new MilvusProperties();
    private JwtProperties jwt = new JwtProperties();
    private GitHubProperties github = new GitHubProperties();
    private AuthProperties auth = new AuthProperties();
    private RagProperties rag = new RagProperties();
    private InterviewProperties interview = new InterviewProperties();
    private ObservabilityProperties observability = new ObservabilityProperties();

    @Data
    public static class MilvusProperties {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 19530;
    }

    @Data
    public static class RagProperties {
        private int topK = 8;
        private int rerankTopN = 3;
    }

    @Data
    public static class InterviewProperties {
        private int maxQuestions = 5;
        private int answerTimeoutSeconds = 300;
    }

    @Data
    public static class JwtProperties {
        private String secret = "interview-agent-default-secret";
        private long expiration = 86400000; // 24 hours
    }

    @Data
    public static class GitHubProperties {
        private String token = "";
    }

    @Data
    public static class AuthProperties {
        private boolean enabled = false;
    }

    @Data
    public static class ObservabilityProperties {
        private boolean enabled = true;
        private int retainDays = 14;
        private int queueCapacity = 10000;
        private int flushBatchSize = 100;
        private boolean storePrompt = false;
        private String adminToken = "";
        private String costCurrency = "CNY";
        private String defaultModel = "qwen-plus";
        /** WS types that attach traceId; use * for all outbound types */
        private String attachTraceIdTypes = "*";
        private Map<String, ModelPricing> pricing = defaultPricing();

        private static Map<String, ModelPricing> defaultPricing() {
            Map<String, ModelPricing> map = new HashMap<>();
            ModelPricing qwen = new ModelPricing();
            qwen.setInputPer1k(0.0008);
            qwen.setOutputPer1k(0.002);
            map.put("qwen-plus", qwen);
            map.put("default", qwen);
            return map;
        }
    }

    @Data
    public static class ModelPricing {
        private double inputPer1k = 0.0008;
        private double outputPer1k = 0.002;
    }
}
