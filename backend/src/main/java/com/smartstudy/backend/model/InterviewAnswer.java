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

/** A question the interviewer asked, the learner's answer, and the grade and feedback it received. */
@Entity
@Table(name = "interview_answers")
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(nullable = false)
    private int questionNumber;

    @Column(nullable = false, length = 4000)
    private String question;

    @Column(nullable = false, length = 12000)
    private String answer;

    @Column(nullable = false)
    private double score;

    @Column(length = 4000)
    private String feedback;

    @Column(nullable = false)
    private Instant answeredAt = Instant.now();

    protected InterviewAnswer() {
    }

    public InterviewAnswer(InterviewSession session, int questionNumber, String question, String answer,
                           double score, String feedback) {
        this.session = session;
        this.questionNumber = questionNumber;
        this.question = question;
        this.answer = answer;
        this.score = score;
        this.feedback = feedback;
    }

    public Long getId() { return id; }
    public InterviewSession getSession() { return session; }
    public int getQuestionNumber() { return questionNumber; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public double getScore() { return score; }
    public String getFeedback() { return feedback; }
    public Instant getAnsweredAt() { return answeredAt; }
}
