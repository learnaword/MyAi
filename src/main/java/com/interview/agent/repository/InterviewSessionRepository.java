package com.interview.agent.repository;

import com.interview.agent.model.entity.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, String> {
}
