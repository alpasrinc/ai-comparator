package com.example.aicomparator.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "debate_messages",
    indexes = {
        @Index(
            name = "idx_debate_messages_debate_id",
            columnList = "debate_id"
        )
    }
)
public class DebateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debate_id", nullable = false)
    private Debate debate;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebateMessageRole role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DebateMessage() {
    }

    private DebateMessage(
        Debate debate,
        Integer roundNumber,
        AiProviderType provider,
        DebateMessageRole role,
        String content
    ) {
        this.debate = Objects.requireNonNull(debate);
        this.roundNumber = roundNumber;
        this.provider = Objects.requireNonNull(provider);
        this.role = Objects.requireNonNull(role);
        this.content = Objects.requireNonNull(content);
    }

    public static DebateMessage participant(
        Debate debate,
        int roundNumber,
        AiProviderType provider,
        String content
    ) {
        return new DebateMessage(
            debate,
            roundNumber,
            provider,
            DebateMessageRole.PARTICIPANT,
            content
        );
    }

    public static DebateMessage synthesis(
        Debate debate,
        AiProviderType provider,
        String content
    ) {
        return new DebateMessage(
            debate,
            null,
            provider,
            DebateMessageRole.SYNTHESIS,
            content
        );
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Debate getDebate() {
        return debate;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public AiProviderType getProvider() {
        return provider;
    }

    public DebateMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
