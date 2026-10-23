package com.smartstudy.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartstudy.backend.model.QuestionType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizQuestionResponse(
        Long id,
        String prompt,
        List<String> options,
        int questionNumber,
        int totalQuestions,
        Integer correctIndex,
        QuestionType questionType) {
}
