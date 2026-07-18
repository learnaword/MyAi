package com.interview.agent.observability.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.config.AppConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ObservabilityAdminFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Obs-Admin-Token";

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/observability/")) {
            return true;
        }
        // 已决议：/status 允许无 Token 探活（仍受 enabled 开关约束，在 doFilter 内处理）
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean statusProbe = path != null && path.equals("/api/observability/status");

        if (!appConfig.getObservability().isEnabled()) {
            write(response, 503, "OBS_DISABLED", "observability is disabled");
            return;
        }
        if (statusProbe) {
            filterChain.doFilter(request, response);
            return;
        }
        String configured = appConfig.getObservability().getAdminToken();
        if (configured == null || configured.isBlank()) {
            write(response, 503, "OBS_ADMIN_NOT_CONFIGURED", "OBS_ADMIN_TOKEN is not configured");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !configured.equals(provided)) {
            write(response, 401, "UNAUTHORIZED", "invalid admin token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", error, "message", message));
    }
}
