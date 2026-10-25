package com.smartstudy.backend.dto;

import java.time.Instant;
import java.util.List;

import com.smartstudy.backend.dto.ml.MlPassageScore;

/** Passages are only returned with a fresh check; saved history keeps the summary scores. */
public record AuthenticityCheckResponse(
        Long id,
        String sourceName,
        int characterCount,
        Instant createdAt,
        double aiProbability,
        double humanProbability,
        boolean aiGenerated,
        double burstinessScore,
        String feedback,
        int passageCount,
        int flaggedPassageCount,
        List<MlPassageScore> passages) {
}
