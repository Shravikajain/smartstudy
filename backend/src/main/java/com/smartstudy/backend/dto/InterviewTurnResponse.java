package com.smartstudy.backend.dto;

/**
 * The interviewer's reply to a start or an answer. {@code feedback} and {@code score} grade the answer just
 * given; on the final answer {@code feedback} is the closing feedback and {@code question} is null.
 */
public record InterviewTurnResponse(
        Long sessionId,
        int questionNumber,
        int questionCount,
        String question,
        String feedback,
        double score,
        Double averageScore,
        boolean complete) {
}
