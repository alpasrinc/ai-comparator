package com.example.aicomparator.dto;

public record DebateParticipantDoneEvent(
        int round,
        String provider,
        Long messageId,
        String content,
        TokenUsage usage
) {
}
