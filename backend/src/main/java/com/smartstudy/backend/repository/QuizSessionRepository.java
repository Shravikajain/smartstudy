package com.smartstudy.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstudy.backend.model.QuizSession;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {
}
