package com.smartstudy.backend.dto.ml;

import java.util.List;

public record MlGenerateResponse(List<MlGeneratedQuestion> questions) {}
