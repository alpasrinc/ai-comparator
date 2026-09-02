package com.example.aicomparator.dto;

/**
 * Bir yapay zekâ çağrısının token muhasebesi.
 *
 * <p>Dikkat: prompt caching devredeyken {@code inputTokens} toplam prompt
 * boyutu <b>değildir</b> — yalnızca cache'lenmemiş kalandır. Toplam =
 * inputTokens + cacheReadTokens + cacheWriteTokens.
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
) {

    public static final TokenUsage EMPTY = new TokenUsage(0, 0, 0, 0);

    public TokenUsage(long inputTokens, long outputTokens) {
        this(inputTokens, outputTokens, 0, 0);
    }

    public long totalTokens() {
        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                cacheReadTokens + other.cacheReadTokens,
                cacheWriteTokens + other.cacheWriteTokens
        );
    }
}
