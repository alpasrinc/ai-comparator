package com.example.aicomparator.dto;

import java.time.Instant;

public record DebateSummaryResponse(
        Long id,
        String topic,
        int rounds,
        String status,
        Instant updatedAt
) {
}
