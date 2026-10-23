package com.smartstudy.backend.dto;

public record StartQuizResponse(Long sessionId, QuizQuestionResponse question,
                                double abilityEstimate) {
}
