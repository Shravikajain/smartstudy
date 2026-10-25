package com.smartstudy.backend.dto;

import java.time.Instant;

import com.smartstudy.backend.model.QuizStatus;

public record QuizHistoryItem(
        Long sessionId,
        Long subjectId,
        String subjectName,
        String questionMode,
        QuizStatus status,
        Instant startedAt,
        Instant completedAt,
        int maxQuestions,
        int answeredCount,
        int correctCount,
        Double averageWrittenScore,
        int aiFlaggedCount) {
}
