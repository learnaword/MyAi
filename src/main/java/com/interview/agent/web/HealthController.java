package com.interview.agent.web;

import com.interview.agent.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final AppConfig appConfig;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "authEnabled", appConfig.getAuth().isEnabled(),
                "milvusEnabled", appConfig.getMilvus().isEnabled()
        );
    }
}
