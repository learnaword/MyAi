
package com.interview.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
