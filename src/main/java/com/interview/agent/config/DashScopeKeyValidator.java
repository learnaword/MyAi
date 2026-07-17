package com.interview.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 启动时探测 DashScope Key，避免面试时才出现难懂的 ChatCompletion 反序列化错误。
 */
@Slf4j
@Component
public class DashScopeKeyValidator implements ApplicationRunner {

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String model;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(apiKey)) {
            log.error("""
                    [DashScope] DASHSCOPE_API_KEY 为空。
                    请在项目根目录 .env 中配置有效 Key（阿里云百炼控制台创建），然后重启。
                    """);
            return;
        }
        try {
            String body = """
                    {"model":"%s","input":{"messages":[{"role":"user","content":"ping"}]},"parameters":{"result_format":"message"}}
                    """.formatted(model);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body() == null ? "" : response.body();
            if (response.statusCode() == 200 && respBody.contains("\"output\"")) {
                log.info("[DashScope] API Key 校验通过，model={}", model);
                return;
            }
            String code = extract(respBody, "\"code\"");
            String message = extract(respBody, "\"message\"");
            log.error("""
                    [DashScope] API Key 校验失败 status={} code={} message={}
                    这会导致面试流程报：Error while extracting response for type ChatCompletion。
                    请到 https://bailian.console.aliyun.com/ 重新创建 API-KEY，写入项目根目录 .env 的 DASHSCOPE_API_KEY 后重启。
                    """, response.statusCode(), code, message);
        } catch (Exception e) {
            log.warn("[DashScope] 启动校验跳过（网络异常）: {}", e.getMessage());
        }
    }

    private static String extract(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) {
            return "";
        }
        int colon = json.indexOf(':', i);
        int firstQuote = json.indexOf('"', colon + 1);
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) {
            return "";
        }
        return json.substring(firstQuote + 1, secondQuote);
    }
}
