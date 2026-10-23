package com.smartstudy.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private int selectedIndex;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private double abilityBefore;

    @Column(nullable = false)
    private double abilityAfter;

    protected QuizAnswer() {
    }

    public QuizAnswer(QuizSession session, Question question, int selectedIndex,
                      boolean correct, double abilityBefore, double abilityAfter) {
        this.session = session;
        this.question = question;
        this.selectedIndex = selectedIndex;
        this.correct = correct;
        this.abilityBefore = abilityBefore;
        this.abilityAfter = abilityAfter;
    }

    public Long getId() { return id; }
    public QuizSession getSession() { return session; }
    public Question getQuestion() { return question; }
    public int getSelectedIndex() { return selectedIndex; }
    public boolean isCorrect() { return correct; }
    public double getAbilityBefore() { return abilityBefore; }
    public double getAbilityAfter() { return abilityAfter; }
}
