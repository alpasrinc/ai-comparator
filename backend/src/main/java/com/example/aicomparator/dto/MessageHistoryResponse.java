package com.example.aicomparator.dto;

import java.time.Instant;
import java.util.List;

public record MessageHistoryResponse(
        Long id,
        Long parentMessageId,
        String role,
        String provider,
        String content,
        Instant createdAt,
        TokenUsage usage,
        List<RetrievedChunk> sources
) {
}
