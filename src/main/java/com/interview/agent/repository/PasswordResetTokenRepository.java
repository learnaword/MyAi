package com.interview.agent.repository;

import com.interview.agent.model.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    List<PasswordResetTokenEntity> findByEmailAndUsedAtIsNullOrderByCreatedAtDesc(String email);

    Optional<PasswordResetTokenEntity> findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(String email);
}
