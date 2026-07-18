package com.interview.agent.config;

import com.interview.agent.auth.JwtService;
import com.interview.agent.auth.UserRole;
import com.interview.agent.model.entity.UserEntity;
import com.interview.agent.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_ROLE = "role";

    private final AppConfig appConfig;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!appConfig.getAuth().isEnabled()) {
            return true;
        }
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        if (!StringUtils.hasText(token)) {
            log.info("[WS] handshake rejected: missing token");
            return false;
        }
        try {
            Claims claims = jwtService.parse(token.trim());
            Long userId = jwtService.userId(claims);
            String role = jwtService.role(claims);
            int pv = jwtService.passwordVersion(claims);
            if (!UserRole.USER.name().equals(role)) {
                log.info("[WS] handshake rejected: role={}", role);
                return false;
            }
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return false;
            }
            int currentPv = user.getPasswordVersion() == null ? 0 : user.getPasswordVersion();
            if (currentPv != pv || !UserRole.USER.name().equals(user.getRole())) {
                return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_USERNAME, user.getUsername());
            attributes.put(ATTR_ROLE, user.getRole());
            return true;
        } catch (Exception e) {
            log.info("[WS] handshake rejected: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
