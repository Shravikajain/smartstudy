package com.smartstudy.backend.dto;

import java.util.List;

public record GeneratedQuestionsResponse(List<String> topics,
                                         List<GeneratedQuestionResponse> questions) {}
