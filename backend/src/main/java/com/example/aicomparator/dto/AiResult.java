package com.example.aicomparator.dto;

/**
 * Bir sağlayıcının senkron (streaming olmayan) çağrısından dönen
 * içerik ve token kullanımını taşır.
 */
public record AiResult(String content, TokenUsage usage) {
}
