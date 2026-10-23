package com.smartstudy.backend.dto;

import java.time.LocalDateTime;

import com.smartstudy.backend.dto.ml.MlAnalyzeResponse;

public record SubjectAnalysisResponse(MlAnalyzeResponse result, LocalDateTime analyzedAt) {}
