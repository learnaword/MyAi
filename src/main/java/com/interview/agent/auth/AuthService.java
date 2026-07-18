package com.interview.agent.auth;

import com.interview.agent.config.AppConfig;
import com.interview.agent.model.entity.PasswordResetTokenEntity;
import com.interview.agent.model.entity.UserEntity;
import com.interview.agent.repository.PasswordResetTokenRepository;
import com.interview.agent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_MAX_FAIL = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final LoginRateLimiter loginRateLimiter;
    private final AppConfig appConfig;

    @Transactional
    public Map<String, Object> register(String username, String email, String password) {
        String u = requireUsername(username);
        String e = requireEmail(email);
        PasswordRules.validate(password);
        if (userRepository.existsByUsername(u)) {
            throw AuthException.conflict("username exists");
        }
        if (userRepository.existsByEmailIgnoreCase(e)) {
            throw AuthException.conflict("email exists");
        }
        UserEntity user = userRepository.save(UserEntity.builder()
                .username(u)
                .email(e)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.USER.name())
                .passwordVersion(0)
                .build());
        return tokenResponse(user);
    }

    public Map<String, Object> login(String username, String password, String clientKey) {
        String key = "login:" + (clientKey == null ? "" : clientKey) + ":" + (username == null ? "" : username.trim());
        loginRateLimiter.check(key);
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw AuthException.badRequest("username/password required");
        }
        UserEntity user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> {
                    loginRateLimiter.recordFailure(key);
                    return AuthException.notFound("USER_NOT_FOUND", "user not found");
                });
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginRateLimiter.recordFailure(key);
            throw AuthException.unauthorized("BAD_CREDENTIALS", "bad password");
        }
        loginRateLimiter.clear(key);
        return tokenResponse(user);
    }

    public Map<String, Object> me(UserEntity user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        body.put("email", user.getEmail());
        body.put("role", user.getRole());
        body.put("emailBound", StringUtils.hasText(user.getEmail()));
        return body;
    }

    @Transactional
    public Map<String, Object> bindEmail(UserEntity user, String email) {
        String e = requireEmail(email);
        if (StringUtils.hasText(user.getEmail())) {
            throw AuthException.badRequest("email already bound");
        }
        if (userRepository.existsByEmailIgnoreCase(e)) {
            throw AuthException.conflict("email exists");
        }
        user.setEmail(e);
        userRepository.save(user);
        return me(user);
    }

    @Transactional
    public void changePassword(UserEntity user, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw AuthException.badRequest("oldPassword/newPassword required");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw AuthException.unauthorized("BAD_CREDENTIALS", "bad password");
        }
        PasswordRules.validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordVersion(user.getPasswordVersion() == null ? 1 : user.getPasswordVersion() + 1);
        userRepository.save(user);
    }

    @Transactional
    public void sendResetCode(String email) {
        String e = requireEmail(email);
        UserEntity user = userRepository.findByEmailIgnoreCase(e)
                .orElseThrow(() -> AuthException.notFound("EMAIL_NOT_FOUND", "email not registered"));
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetTokenEntity token = PasswordResetTokenEntity.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .codeHash(hashCode(user.getEmail(), code))
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .failCount(0)
                .build();
        resetTokenRepository.save(token);
        mailService.sendPasswordResetCode(user.getEmail(), code);
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        String e = requireEmail(email);
        if (!StringUtils.hasText(code)) {
            throw AuthException.badRequest("code required");
        }
        PasswordRules.validate(newPassword);
        PasswordResetTokenEntity token = resetTokenRepository.findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(e)
                .orElseThrow(() -> AuthException.unauthorized("INVALID_CODE", "invalid or expired code"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw AuthException.unauthorized("INVALID_CODE", "invalid or expired code");
        }
        if (token.getFailCount() != null && token.getFailCount() >= CODE_MAX_FAIL) {
            throw AuthException.unauthorized("INVALID_CODE", "invalid or expired code");
        }
        if (!token.getCodeHash().equals(hashCode(e, code.trim()))) {
            token.setFailCount(token.getFailCount() == null ? 1 : token.getFailCount() + 1);
            resetTokenRepository.save(token);
            throw AuthException.unauthorized("INVALID_CODE", "invalid or expired code");
        }
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> AuthException.notFound("USER_NOT_FOUND", "user not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordVersion(user.getPasswordVersion() == null ? 1 : user.getPasswordVersion() + 1);
        userRepository.save(user);
        token.setUsedAt(Instant.now());
        resetTokenRepository.save(token);
    }

    @Transactional
    public Map<String, Object> createAdmin(String username, String email, String password) {
        String u = requireUsername(username);
        String e = requireEmail(email);
        PasswordRules.validate(password);
        if (userRepository.existsByUsername(u)) {
            throw AuthException.conflict("username exists");
        }
        if (userRepository.existsByEmailIgnoreCase(e)) {
            throw AuthException.conflict("email exists");
        }
        UserEntity user = userRepository.save(UserEntity.builder()
                .username(u)
                .email(e)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.ADMIN.name())
                .passwordVersion(0)
                .build());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        body.put("email", user.getEmail());
        body.put("role", user.getRole());
        return body;
    }

    public UserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("UNAUTHORIZED", "invalid token"));
    }

    private Map<String, Object> tokenResponse(UserEntity user) {
        int pv = user.getPasswordVersion() == null ? 0 : user.getPasswordVersion();
        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole(), pv);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        body.put("email", user.getEmail());
        body.put("role", user.getRole());
        return body;
    }

    private String requireUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw AuthException.badRequest("username required");
        }
        String u = username.trim();
        if (u.length() > 64) {
            throw AuthException.badRequest("username too long");
        }
        return u;
    }

    private String requireEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw AuthException.badRequest("email required");
        }
        String e = email.trim();
        if (!e.contains("@") || e.length() > 255) {
            throw AuthException.badRequest("invalid email");
        }
        return e;
    }

    private String hashCode(String email, String code) {
        try {
            String pepper = appConfig.getAuth().getResetCodePepper();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((email.toLowerCase() + ":" + code + ":" + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("hash failed", e);
        }
    }
}
