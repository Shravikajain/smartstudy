package com.smartstudy.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_papers")
public class QuestionPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String storedFilename;

    private Integer examYear;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    public QuestionPaper() {
    }

    public QuestionPaper(String originalFilename, String storedFilename, Integer examYear,
                         String extractedText, Subject subject) {
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.examYear = examYear;
        this.extractedText = extractedText;
        this.subject = subject;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public Integer getExamYear() { return examYear; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public String getExtractedText() { return extractedText; }
    public Subject getSubject() { return subject; }
}