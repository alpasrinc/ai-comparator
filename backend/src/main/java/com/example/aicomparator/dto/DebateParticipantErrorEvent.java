package com.example.aicomparator.dto;

public record DebateParticipantErrorEvent(
        int round,
        String provider,
        String message
) {
}
