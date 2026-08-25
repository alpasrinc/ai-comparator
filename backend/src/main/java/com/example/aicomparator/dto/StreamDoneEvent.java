package com.example.aicomparator.dto;

public record StreamDoneEvent(String provider, Long messageId, String content) {
}
