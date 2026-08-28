package com.example.aicomparator.dto;

/**
 * Bir yapay zekâ çağrısının tükettiği giriş/çıkış token sayıları.
 */
public record TokenUsage(long inputTokens, long outputTokens) {

    public static final TokenUsage EMPTY = new TokenUsage(0, 0);

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens
        );
    }
}
