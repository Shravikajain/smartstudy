package com.smartstudy.backend.dto.ml;

public record MlDetectResponse(boolean isAiGenerated, double aiProbability,
                               double humanProbability, double burstinessScore,
                               String feedback) {}
