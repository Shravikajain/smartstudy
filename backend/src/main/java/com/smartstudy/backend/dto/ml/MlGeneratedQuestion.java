package com.smartstudy.backend.dto.ml;

import java.util.List;

public record MlGeneratedQuestion(String prompt, List<String> options, Integer correctIndex,
                                  String explanation, int difficulty, String questionType,
                                  String referenceAnswer) {
    public MlGeneratedQuestion(String prompt, List<String> options, int correctIndex,
                               String explanation, int difficulty) {
        this(prompt, options, correctIndex, explanation, difficulty, "multiple_choice", null);
    }
}
