package com.smartstudy.backend.dto;

import java.time.Instant;
import java.util.List;

/** A saved interview with every answer; {@code currentQuestion} is set while it can still be continued. */
public record InterviewReviewResponse(
        InterviewSummary summary,
        int questionNumber,
        String currentQuestion,
        String summaryFeedback,
        List<Answer> answers) {

    public record Answer(
            int questionNumber,
            String question,
            String answer,
            double score,
            String feedback,
            Instant answeredAt) {
    }
}
