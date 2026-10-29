package com.smartstudy.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstudy.backend.model.InterviewSession;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findByUserIdOrderByIdDesc(Long userId);
}
