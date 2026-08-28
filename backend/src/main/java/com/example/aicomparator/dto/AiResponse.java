package com.example.aicomparator.dto;

public record AiResponse(
        Long messageId,
        String provider,
        String content,
        String error,
        TokenUsage usage
) {

    public AiResponse(Long messageId, String provider, String content) {
        this(messageId, provider, content, null, TokenUsage.EMPTY);
    }

    public static AiResponse success(
            Long messageId,
            String provider,
            String content
    ) {
        return new AiResponse(messageId, provider, content, null,
                TokenUsage.EMPTY);
    }

    public static AiResponse success(
            Long messageId,
            String provider,
            String content,
            TokenUsage usage
    ) {
        return new AiResponse(messageId, provider, content, null, usage);
    }

    public static AiResponse failure(String provider, String error) {
        return new AiResponse(null, provider, null, error, TokenUsage.EMPTY);
    }
}
