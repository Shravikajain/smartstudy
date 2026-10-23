package com.smartstudy.backend.dto.ml;
import java.util.List;
public record MlHotQuestion(String text, int occurrences, List<Integer> years, List<Long> paperIds) {}