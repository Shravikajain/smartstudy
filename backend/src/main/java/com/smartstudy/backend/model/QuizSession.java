package com.smartstudy.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_sessions")
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private int maxQuestions;

    @Column(nullable = false)
    private int answeredCount;

    @Column(nullable = false)
    private int correctCount;

    @Column(nullable = false)
    private double abilityEstimate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizStatus status = QuizStatus.IN_PROGRESS;

    @ManyToOne
    @JoinColumn(name = "current_question_id")
    private Question currentQuestion;

    @ManyToMany
    @JoinTable(name = "quiz_session_questions",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "question_order")
    private List<Question> questionPool = new ArrayList<>();

    protected QuizSession() {
    }

    public QuizSession(User user, Subject subject, int maxQuestions) {
        this.user = user;
        this.subject = subject;
        this.maxQuestions = maxQuestions;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Subject getSubject() { return subject; }
    public int getMaxQuestions() { return maxQuestions; }
    public int getAnsweredCount() { return answeredCount; }
    public int getCorrectCount() { return correctCount; }
    public double getAbilityEstimate() { return abilityEstimate; }
    public QuizStatus getStatus() { return status; }
    public Question getCurrentQuestion() { return currentQuestion; }
    public List<Question> getQuestionPool() { return List.copyOf(questionPool); }

    public void setCurrentQuestion(Question currentQuestion) {
        this.currentQuestion = currentQuestion;
    }

    public void setQuestionPool(List<Question> questionPool) {
        this.questionPool = new ArrayList<>(questionPool);
    }

    public void recordAnswer(boolean correct, double newAbilityEstimate) {
        answeredCount++;
        if (correct) {
            correctCount++;
        }
        abilityEstimate = newAbilityEstimate;
    }

    public void finish() {
        status = QuizStatus.COMPLETED;
        currentQuestion = null;
    }
}
