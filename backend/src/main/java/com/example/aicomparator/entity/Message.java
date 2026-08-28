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
    name = "messages",
    indexes = {
        @Index(
            name = "idx_messages_conversation_id",
            columnList = "conversation_id"
        ),
        @Index(
            name = "idx_messages_parent_message_id",
            columnList = "parent_message_id"
        )
    }
)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private Message parentMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiProviderType provider;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
    }

    private Message(
        Conversation conversation,
        Message parentMessage,
        MessageRole role,
        AiProviderType provider,
        String content,
        Long inputTokens,
        Long outputTokens
    ) {
        this.conversation = Objects.requireNonNull(conversation);
        this.parentMessage = parentMessage;
        this.role = Objects.requireNonNull(role);
        this.provider = provider;
        this.content = Objects.requireNonNull(content);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public static Message createUserMessage(
        Conversation conversation,
        Message parentMessage,
        String content
    ) {
        return new Message(
            conversation,
            parentMessage,
            MessageRole.USER,
            null,
            content,
            null,
            null
        );
    }

    public static Message createAssistantMessage(
        Conversation conversation,
        Message parentMessage,
        AiProviderType provider,
        String content
    ) {
        return createAssistantMessage(
            conversation, parentMessage, provider, content, null, null);
    }

    public static Message createAssistantMessage(
        Conversation conversation,
        Message parentMessage,
        AiProviderType provider,
        String content,
        Long inputTokens,
        Long outputTokens
    ) {
        return new Message(
            conversation,
            parentMessage,
            MessageRole.ASSISTANT,
            Objects.requireNonNull(provider),
            content,
            inputTokens,
            outputTokens
        );
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Message getParentMessage() {
        return parentMessage;
    }

    public MessageRole getRole() {
        return role;
    }

    public AiProviderType getProvider() {
        return provider;
    }

    public String getContent() {
        return content;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}