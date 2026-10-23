package com.smartstudy.backend.dto.ml;

public record MlInterviewTurnResponse(String question, String feedback,
                                     double score, boolean complete) {}
