package com.smartstudy.backend.dto.ml;
import java.util.List;

public record MlCluster(int clusterId, List<String> keywords, int questionCount,
                        int papersAppearedIn, double reappearanceScore,
                        List<MlQuestion> questions) {}
