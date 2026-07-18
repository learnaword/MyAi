package com.interview.agent.observability.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.auth.JwtService;
import com.interview.agent.auth.UserRole;
import com.interview.agent.config.AppConfig;
import com.interview.agent.model.entity.UserEntity;
import com.interview.agent.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ObservabilityAdminFilter extends OncePerRequestFilter {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/observability/");
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

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            write(response, 401, "UNAUTHORIZED", "admin jwt required");
            return;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            String role = jwtService.role(claims);
            if (!UserRole.ADMIN.name().equals(role)) {
                write(response, 403, "FORBIDDEN", "admin role required");
                return;
            }
            Long userId = jwtService.userId(claims);
            int pv = jwtService.passwordVersion(claims);
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                write(response, 401, "UNAUTHORIZED", "invalid admin token");
                return;
            }
            int currentPv = user.getPasswordVersion() == null ? 0 : user.getPasswordVersion();
            if (currentPv != pv || !UserRole.ADMIN.name().equals(user.getRole())) {
                write(response, 401, "UNAUTHORIZED", "invalid admin token");
                return;
            }
        } catch (Exception e) {
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
