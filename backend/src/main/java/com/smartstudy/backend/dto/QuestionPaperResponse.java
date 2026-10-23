package com.smartstudy.backend.dto;

import java.time.LocalDateTime;

public class QuestionPaperResponse {
    private Long id;
    private String originalFilename;
    private Integer examYear;
    private LocalDateTime uploadedAt;
    private Long subjectId;
    private int textLength;

    public QuestionPaperResponse(Long id, String originalFilename, Integer examYear,
                                 LocalDateTime uploadedAt, Long subjectId, int textLength) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.examYear = examYear;
        this.uploadedAt = uploadedAt;
        this.subjectId = subjectId;
        this.textLength = textLength;
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public Integer getExamYear() { return examYear; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public Long getSubjectId() { return subjectId; }
    public int getTextLength() { return textLength; }
}