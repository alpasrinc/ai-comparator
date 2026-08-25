package com.example.aicomparator.dto;

public record AiResponse(
        Long messageId,
        String provider,
        String content,
        String error
) {

    public AiResponse(Long messageId, String provider, String content) {
        this(messageId, provider, content, null);
    }

    public static AiResponse success(
            Long messageId,
            String provider,
            String content
    ) {
        return new AiResponse(messageId, provider, content, null);
    }

    public static AiResponse failure(String provider, String error) {
        return new AiResponse(null, provider, null, error);
    }
}
