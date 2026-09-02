package com.example.aicomparator.dto;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String filename,
        long sizeBytes,
        int chunkCount,
        Instant createdAt
) {
}
