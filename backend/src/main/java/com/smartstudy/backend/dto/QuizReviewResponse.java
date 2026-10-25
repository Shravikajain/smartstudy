package com.smartstudy.backend.dto;

import java.util.List;

public record QuizReviewResponse(QuizHistoryItem summary, List<AnswerReview> answers) {
}
