package com.example.aicomparator.dto;

public record ActiveMessageResponse(
        Long conversationId,
        Long activeMessageId,
        String provider
) {
}