package com.example.aicomparator.dto;

public record DebateMessageResponse(
        Long id,
        Integer roundNumber,
        String provider,
        String role,
        String content
) {
}
