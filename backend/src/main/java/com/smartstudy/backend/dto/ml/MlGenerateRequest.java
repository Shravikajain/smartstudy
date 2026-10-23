package com.smartstudy.backend.dto.ml;

public record MlGenerateRequest(String subjectName, String topicName, String context, int count,
                                String questionType) {
    public MlGenerateRequest(String subjectName, String topicName, String context, int count) {
        this(subjectName, topicName, context, count, "multiple_choice");
    }
}
