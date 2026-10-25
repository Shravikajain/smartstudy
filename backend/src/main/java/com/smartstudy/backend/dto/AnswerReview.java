package com.smartstudy.backend.dto;

import java.time.Instant;
import java.util.List;

import com.smartstudy.backend.model.QuestionType;

/** One answered question as shown when a learner reviews a past test. */
public record AnswerReview(
        int number,
        Long questionId,
        String topic,
        QuestionType questionType,
        String prompt,
        List<String> options,
        Integer selectedIndex,
        Integer correctIndex,
        boolean correct,
        String explanation,
        String answerText,
        String referenceAnswer,
        Double score,
        String feedback,
        Double aiProbability,
        Boolean aiFlagged,
        Instant answeredAt) {
}
