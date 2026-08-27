package com.example.aicomparator.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "debates")
public class Debate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String topic;

    @Column(nullable = false)
    private int rounds;

    @Enumerated(EnumType.STRING)
    @Column(name = "synthesizer_provider", nullable = false, length = 20)
    private AiProviderType synthesizerProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebateStatus status;

    @Column(name = "final_answer", columnDefinition = "LONGTEXT")
    private String finalAnswer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "debate_participants",
        joinColumns = @JoinColumn(name = "debate_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private Set<AiProviderType> participants = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Debate() {
    }

    public Debate(
        String topic,
        int rounds,
        AiProviderType synthesizerProvider,
        Set<AiProviderType> participants
    ) {
        this.topic = Objects.requireNonNull(topic);
        this.rounds = rounds;
        this.synthesizerProvider = Objects.requireNonNull(synthesizerProvider);
        this.participants = new LinkedHashSet<>(participants);
        this.status = DebateStatus.RUNNING;
    }

    public void complete(String finalAnswer) {
        this.finalAnswer = finalAnswer;
        this.status = DebateStatus.COMPLETED;
    }

    public void completeWithoutSynthesis() {
        this.status = DebateStatus.COMPLETED;
    }

    public void fail() {
        this.status = DebateStatus.FAILED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public int getRounds() {
        return rounds;
    }

    public AiProviderType getSynthesizerProvider() {
        return synthesizerProvider;
    }

    public DebateStatus getStatus() {
        return status;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public Set<AiProviderType> getParticipants() {
        return participants;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
