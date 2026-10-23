package com.smartstudy.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartstudy.backend.dto.ml.MlDetectResponse;

public record AnswerResult(
        boolean correct,
        Integer correctIndex,
        String explanation,
        double abilityEstimate,
        int answeredCount,
        int correctCount,
        boolean finished,
        QuizQuestionResponse nextQuestion,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Double score,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String gradingFeedback,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        MlDetectResponse aiWritingAssessment) {
}
