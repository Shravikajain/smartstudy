package com.smartstudy.backend.dto.ml;

public record MlGradeAnswerRequest(String prompt, String referenceAnswer, String rubric,
                                   String studentAnswer) {}
