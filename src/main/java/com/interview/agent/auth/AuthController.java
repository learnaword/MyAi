package com.interview.agent.auth;

import com.interview.agent.model.entity.UserEntity;
import com.interview.agent.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody AuthRequest req) {
        if (req.getUsername() == null || req.getPassword() == null
                || req.getUsername().isBlank() || req.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username/password required");
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username exists");
        }
        UserEntity user = userRepository.save(UserEntity.builder()
                .username(req.getUsername().trim())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .build());
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return Map.of("token", token, "userId", user.getId(), "username", user.getUsername());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AuthRequest req) {
        UserEntity user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return Map.of("token", token, "userId", user.getId(), "username", user.getUsername());
    }

    @Data
    public static class AuthRequest {
        private String username;
        private String password;
    }
}
