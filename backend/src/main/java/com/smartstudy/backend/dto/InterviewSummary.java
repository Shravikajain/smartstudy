package com.smartstudy.backend.dto;

import java.time.Instant;

public record InterviewSummary(
        Long sessionId,
        String targetType,
        String target,
        String level,
        int questionCount,
        int answeredCount,
        Double averageScore,
        boolean completed,
        Instant startedAt,
        Instant completedAt) {
}
