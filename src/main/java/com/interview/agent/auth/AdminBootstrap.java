package com.interview.agent.auth;

import com.interview.agent.config.AppConfig;
import com.interview.agent.model.entity.UserEntity;
import com.interview.agent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppConfig appConfig;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String username = appConfig.getAuth().getBootstrapAdminUsername();
        String password = appConfig.getAuth().getBootstrapAdminPassword();
        String email = appConfig.getAuth().getBootstrapAdminEmail();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.info("[Auth] bootstrap admin skipped (set ADMIN_BOOTSTRAP_PASSWORD to enable)");
            return;
        }
        if (userRepository.existsByUsername(username.trim())) {
            log.info("[Auth] bootstrap admin already exists: {}", username);
            return;
        }
        if (StringUtils.hasText(email) && userRepository.existsByEmailIgnoreCase(email.trim())) {
            log.warn("[Auth] bootstrap admin email already used, skip create");
            return;
        }
        userRepository.save(UserEntity.builder()
                .username(username.trim())
                .email(StringUtils.hasText(email) ? email.trim() : null)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.ADMIN.name())
                .passwordVersion(0)
                .build());
        log.info("[Auth] bootstrap admin created: {}", username);
    }
}
