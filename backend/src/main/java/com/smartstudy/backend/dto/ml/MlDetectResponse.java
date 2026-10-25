package com.smartstudy.backend.dto.ml;

import java.util.List;

public record MlDetectResponse(boolean isAiGenerated, double aiProbability,
                               double humanProbability, double burstinessScore,
                               String feedback, List<MlPassageScore> passages) {
    public MlDetectResponse {
        passages = passages == null ? List.of() : List.copyOf(passages);
    }

    public MlDetectResponse(boolean isAiGenerated, double aiProbability, double humanProbability,
                            double burstinessScore, String feedback) {
        this(isAiGenerated, aiProbability, humanProbability, burstinessScore, feedback, List.of());
    }
}
