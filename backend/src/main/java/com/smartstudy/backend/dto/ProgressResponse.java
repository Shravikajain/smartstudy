package com.smartstudy.backend.dto;

import java.time.Instant;
import java.util.List;

/** A learner's results over time: one point per test (oldest first) and totals per topic. */
public record ProgressResponse(List<TestPoint> tests, List<TopicScore> topics) {

    public record TestPoint(
            Long sessionId,
            Long subjectId,
            String subjectName,
            Instant startedAt,
            int answeredCount,
            int correctCount,
            Double averageWrittenScore,
            int aiFlaggedCount,
            double abilityEstimate) {
    }

    public record TopicScore(
            Long topicId,
            String topicName,
            Long subjectId,
            String subjectName,
            int answeredCount,
            int correctCount,
            Double averageWrittenScore) {
    }
}
