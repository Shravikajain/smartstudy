package com.smartstudy.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstudy.backend.model.QuizSession;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {
    List<QuizSession> findByUserIdOrderByIdDesc(Long userId);
}
