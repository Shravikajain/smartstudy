package com.smartstudy.backend.dto.ml;

import java.util.List;

public record MlGenerateRequest(String subjectName, String topicName, String context, int count,
                                String questionType, List<String> topicNames) {
    public MlGenerateRequest(String subjectName, String topicName, String context, int count) {
        this(subjectName, topicName, context, count, "multiple_choice", List.of());
    }

    public MlGenerateRequest(String subjectName, String topicName, String context, int count,
                             String questionType) {
        this(subjectName, topicName, context, count, questionType, List.of());
    }
}
