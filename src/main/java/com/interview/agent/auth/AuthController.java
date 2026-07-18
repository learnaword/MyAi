package com.interview.agent.auth;

import com.interview.agent.model.entity.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest req) {
        return authService.register(req.getUsername(), req.getEmail(), req.getPassword());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        String clientKey = request.getRemoteAddr();
        return authService.login(req.getUsername(), req.getPassword(), clientKey);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return authService.me(currentUser(authentication));
    }

    @PostMapping("/me/bind-email")
    public Map<String, Object> bindEmail(Authentication authentication, @RequestBody BindEmailRequest req) {
        return authService.bindEmail(currentUser(authentication), req.getEmail());
    }

    @PostMapping("/password/change")
    public Map<String, Object> changePassword(Authentication authentication, @RequestBody ChangePasswordRequest req) {
        authService.changePassword(currentUser(authentication), req.getOldPassword(), req.getNewPassword());
        return Map.of("ok", true);
    }

    @PostMapping("/password/forgot/send-code")
    public Map<String, Object> sendResetCode(@RequestBody ForgotSendRequest req) {
        authService.sendResetCode(req.getEmail());
        return Map.of("ok", true);
    }

    @PostMapping("/password/forgot/reset")
    public Map<String, Object> resetPassword(@RequestBody ForgotResetRequest req) {
        authService.resetPassword(req.getEmail(), req.getCode(), req.getNewPassword());
        return Map.of("ok", true);
    }

    @PostMapping("/admin/users")
    public Map<String, Object> createAdmin(Authentication authentication, @RequestBody RegisterRequest req) {
        UserEntity actor = currentUser(authentication);
        if (!UserRole.ADMIN.name().equals(actor.getRole())) {
            throw AuthException.forbidden("admin only");
        }
        return authService.createAdmin(req.getUsername(), req.getEmail(), req.getPassword());
    }

    private UserEntity currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof AuthPrincipal principal)) {
            throw AuthException.unauthorized("UNAUTHORIZED", "login required");
        }
        return authService.requireUser(principal.getUserId());
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class BindEmailRequest {
        private String email;
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class ForgotSendRequest {
        private String email;
    }

    @Data
    public static class ForgotResetRequest {
        private String email;
        private String code;
        private String newPassword;
    }
}
