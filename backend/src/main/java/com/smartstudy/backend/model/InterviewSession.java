package com.smartstudy.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One interview practice run. The questions, answers and grades are kept as {@link InterviewAnswer} rows. */
@Entity
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // subject or engineering_role
    @Column(nullable = false, length = 32)
    private String targetType;

    @Column(nullable = false, length = 120)
    private String target;

    @Column(nullable = false, length = 32)
    private String level;

    @Column(nullable = false)
    private int questionCount;

    // The latest question asked, numbered from 1. The text is cleared once the interview finishes.
    private int questionNumber;

    @Column(length = 4000)
    private String currentQuestion;

    @Column(nullable = false)
    private int answeredCount;

    private Double averageScore;

    // The interviewer's closing feedback on the whole session.
    @Column(length = 4000)
    private String summaryFeedback;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    protected InterviewSession() {
    }

    public InterviewSession(User user, String targetType, String target, String level, int questionCount) {
        this.user = user;
        this.targetType = targetType;
        this.target = target;
        this.level = level;
        this.questionCount = questionCount;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTargetType() { return targetType; }
    public String getTarget() { return target; }
    public String getLevel() { return level; }
    public int getQuestionCount() { return questionCount; }
    public int getQuestionNumber() { return questionNumber; }
    public String getCurrentQuestion() { return currentQuestion; }
    public int getAnsweredCount() { return answeredCount; }
    public Double getAverageScore() { return averageScore; }
    public String getSummaryFeedback() { return summaryFeedback; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public boolean isCompleted() { return completedAt != null; }

    public void askNext(String question) {
        questionNumber++;
        currentQuestion = question;
    }

    public void recordAnswer(double score) {
        averageScore = ((averageScore == null ? 0 : averageScore * answeredCount) + score) / (answeredCount + 1);
        answeredCount++;
    }

    public void finish(String summaryFeedback) {
        this.summaryFeedback = summaryFeedback;
        currentQuestion = null;
        completedAt = Instant.now();
    }
}
