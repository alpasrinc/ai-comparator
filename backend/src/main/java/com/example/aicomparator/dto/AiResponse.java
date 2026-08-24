package com.example.aicomparator.dto;

public record AiResponse(
        Long messageId,
        String provider,
        String content
) {
}