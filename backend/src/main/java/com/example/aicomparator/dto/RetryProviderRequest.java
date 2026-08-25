package com.example.aicomparator.dto;

import com.example.aicomparator.entity.AiProviderType;

import jakarta.validation.constraints.NotNull;

public record RetryProviderRequest(
        @NotNull Long conversationId,
        @NotNull Long userMessageId,
        @NotNull AiProviderType provider
) {
}
