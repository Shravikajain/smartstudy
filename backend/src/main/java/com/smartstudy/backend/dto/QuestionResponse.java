package com.smartstudy.backend.dto;

import java.util.List;

public record QuestionResponse(Long id, String prompt, List<String> options,
                               int correctIndex, String explanation, double difficulty,
                               Long topicId) {
}
