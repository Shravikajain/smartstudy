package com.smartstudy.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstudy.backend.model.InterviewAnswer;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {
    List<InterviewAnswer> findBySessionIdOrderByQuestionNumberAsc(Long sessionId);
}
