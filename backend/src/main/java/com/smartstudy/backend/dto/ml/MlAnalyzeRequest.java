package com.smartstudy.backend.dto.ml;
import java.util.List;
public record MlAnalyzeRequest(List<MlPaperIn> papers, int maxClusters, double hotThreshold) {}
