package com.example.aicomparator.dto;

import java.time.Instant;

public record MessageHistoryResponse(
        Long id,
        Long parentMessageId,
        String role,
        String provider,
        String content,
        Instant createdAt
) {
}