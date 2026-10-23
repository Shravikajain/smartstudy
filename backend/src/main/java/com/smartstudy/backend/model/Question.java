package com.smartstudy.backend.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000)
    private String prompt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_order")
    @Column(name = "option_text", nullable = false, length = 1000)
    private List<String> options = new ArrayList<>();

    @Column(nullable = false)
    private int correctIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 32)
    private QuestionType questionType;

    @Column(name = "reference_answer", length = 12000)
    private String referenceAnswer;

    @Column(length = 4000)
    private String explanation;

    @Column(nullable = false)
    private double difficulty;

    @Column(nullable = false)
    private int timesAnswered;

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    protected Question() {
    }

    public Question(Topic topic, String prompt, List<String> options, int correctIndex,
                    String explanation, double difficulty) {
        this(topic, prompt, options, correctIndex, explanation, difficulty,
                QuestionType.MULTIPLE_CHOICE, null);
    }

    public Question(Topic topic, String prompt, List<String> options, int correctIndex,
                    String explanation, double difficulty, QuestionType questionType,
                    String referenceAnswer) {
        this.topic = topic;
        this.prompt = prompt;
        this.options = new ArrayList<>(options);
        this.correctIndex = correctIndex;
        this.explanation = explanation;
        this.difficulty = difficulty;
        this.questionType = questionType;
        this.referenceAnswer = referenceAnswer;
    }

    public Long getId() { return id; }
    public String getPrompt() { return prompt; }
    public List<String> getOptions() { return List.copyOf(options); }
    public int getCorrectIndex() { return correctIndex; }
    public QuestionType getQuestionType() {
        return questionType == null ? QuestionType.MULTIPLE_CHOICE : questionType;
    }
    public String getReferenceAnswer() { return referenceAnswer; }
    public boolean isSubjective() { return getQuestionType() == QuestionType.SUBJECTIVE; }
    public String getExplanation() { return explanation; }
    public double getDifficulty() { return difficulty; }
    public int getTimesAnswered() { return timesAnswered; }
    public Topic getTopic() { return topic; }

    public void setDifficulty(double difficulty) { this.difficulty = difficulty; }

    public void incrementTimesAnswered() {
        timesAnswered++;
    }
}
