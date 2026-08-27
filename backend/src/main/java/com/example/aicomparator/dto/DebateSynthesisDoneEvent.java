package com.example.aicomparator.dto;

public record DebateSynthesisDoneEvent(
        String provider,
        Long messageId,
        String content
) {
}
