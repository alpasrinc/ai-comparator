package com.example.aicomparator.dto;

import java.time.Instant;

public record ConversationSummaryResponse(
        Long id,
        String title,
        Long activeMessageId,
        Instant createdAt,
        Instant updatedAt
) {
}