package com.smartstudy.backend.dto.ml;
import java.util.List;
public record MlAnalyzeResponse(int totalQuestions, int totalPapers,
                                List<MlCluster> clusters, List<MlHotQuestion> hotQuestions) {}
