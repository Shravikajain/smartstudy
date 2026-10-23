package com.smartstudy.backend.dto;

import java.util.List;

import com.smartstudy.backend.model.QuestionType;

public record GeneratedQuestionResponse(Long id, String prompt, List<String> options,
                                        double difficulty, String topic, QuestionType questionType) {}
