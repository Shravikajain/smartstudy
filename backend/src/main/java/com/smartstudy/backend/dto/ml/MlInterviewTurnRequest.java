package com.smartstudy.backend.dto.ml;

import java.util.List;

public record MlInterviewTurnRequest(String targetType, String target, String level,
                                    int questionNumber, int questionCount,
                                    List<MlInterviewMessage> history, String answer) {}
