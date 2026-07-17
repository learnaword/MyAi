package com.interview.agent.memory;

import com.interview.agent.model.entity.WeaknessRecordEntity;
import com.interview.agent.repository.WeaknessRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final WeaknessRecordRepository weaknessRecordRepository;

    @Transactional
    public void recordWeaknesses(Long userId, List<String> topics) {
        if (userId == null || topics == null) {
            return;
        }
        for (String topic : topics) {
            if (topic == null || topic.isBlank()) {
                continue;
            }
            String normalized = topic.trim();
            WeaknessRecordEntity record = weaknessRecordRepository
                    .findByUserIdAndTopic(userId, normalized)
                    .orElse(WeaknessRecordEntity.builder()
                            .userId(userId)
                            .topic(normalized)
                            .hitCount(0)
                            .build());
            record.setHitCount(record.getHitCount() + 1);
            weaknessRecordRepository.save(record);
        }
    }

    public List<String> topWeakTopics(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return weaknessRecordRepository.findByUserIdOrderByHitCountDesc(userId).stream()
                .limit(limit)
                .map(WeaknessRecordEntity::getTopic)
                .toList();
    }
}
