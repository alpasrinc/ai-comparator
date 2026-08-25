package com.example.aicomparator.dto;

import java.time.Instant;
import java.util.List;

public record ConversationDetailResponse(
        Long id,
        String title,
        Long activeMessageId,
        Instant createdAt,
        Instant updatedAt,
        List<MessageHistoryResponse> messages
) {
}