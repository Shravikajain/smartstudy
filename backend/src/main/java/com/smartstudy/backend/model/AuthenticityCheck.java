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

/** Result of checking a learner's own material for AI-written text. The text itself is not kept. */
@Entity
@Table(name = "authenticity_checks")
public class AuthenticityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String sourceName;

    @Column(nullable = false)
    private int characterCount;

    @Column(nullable = false)
    private double aiProbability;

    @Column(nullable = false)
    private double humanProbability;

    @Column(nullable = false)
    private boolean aiGenerated;

    @Column(nullable = false)
    private double burstinessScore;

    @Column(length = 2000)
    private String feedback;

    @Column(nullable = false)
    private int passageCount;

    @Column(nullable = false)
    private int flaggedPassageCount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuthenticityCheck() {
    }

    public AuthenticityCheck(User user, String sourceName, int characterCount, double aiProbability,
                             double humanProbability, boolean aiGenerated, double burstinessScore,
                             String feedback, int passageCount, int flaggedPassageCount) {
        this.user = user;
        this.sourceName = sourceName;
        this.characterCount = characterCount;
        this.aiProbability = aiProbability;
        this.humanProbability = humanProbability;
        this.aiGenerated = aiGenerated;
        this.burstinessScore = burstinessScore;
        this.feedback = feedback;
        this.passageCount = passageCount;
        this.flaggedPassageCount = flaggedPassageCount;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getSourceName() { return sourceName; }
    public int getCharacterCount() { return characterCount; }
    public double getAiProbability() { return aiProbability; }
    public double getHumanProbability() { return humanProbability; }
    public boolean isAiGenerated() { return aiGenerated; }
    public double getBurstinessScore() { return burstinessScore; }
    public String getFeedback() { return feedback; }
    public int getPassageCount() { return passageCount; }
    public int getFlaggedPassageCount() { return flaggedPassageCount; }
    public Instant getCreatedAt() { return createdAt; }
}
