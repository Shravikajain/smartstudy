package com.smartstudy.backend.dto;

public record StartInterviewRequest(String targetType, String target, String level, int questionCount) {
}
