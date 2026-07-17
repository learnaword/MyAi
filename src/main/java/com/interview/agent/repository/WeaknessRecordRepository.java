package com.interview.agent.repository;

import com.interview.agent.model.entity.WeaknessRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeaknessRecordRepository extends JpaRepository<WeaknessRecordEntity, Long> {
    List<WeaknessRecordEntity> findByUserIdOrderByHitCountDesc(Long userId);
    Optional<WeaknessRecordEntity> findByUserIdAndTopic(Long userId, String topic);
}
