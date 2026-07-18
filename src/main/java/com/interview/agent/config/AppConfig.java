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
    private MailProperties mail = new MailProperties();
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
        /** 7 days */
        private long expiration = 604_800_000L;
    }

    @Data
    public static class GitHubProperties {
        private String token = "";
    }

    @Data
    public static class AuthProperties {
        private boolean enabled = true;
        private String bootstrapAdminUsername = "admin";
        private String bootstrapAdminPassword = "";
        private String bootstrapAdminEmail = "admin@localhost";
        private String resetCodePepper = "interview-agent-reset-pepper";
    }

    @Data
    public static class MailProperties {
        private String host = "";
        private Integer port = 587;
        private String username = "";
        private String password = "";
        private String from = "";
    }

    @Data
    public static class ObservabilityProperties {
        private boolean enabled = true;
        private int retainDays = 14;
        private int queueCapacity = 10000;
        private int flushBatchSize = 100;
        private boolean storePrompt = false;
        /** @deprecated removed; kept for binding ignore if present in old env */
        private String adminToken = "";
        private String costCurrency = "CNY";
        private String defaultModel = "qwen-plus";
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
