package com.example.aicomparator.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Metni gömme (embedding) için örtüşmeli parçalara böler.
 *
 * <p>Örtüşme bilinçli: parça sınırına denk gelen bir cümle örtüşme olmadan
 * hiçbir parçada bütün hâlde bulunmaz ve o cümleyi soran soru hiçbir zaman
 * doğru parçayı getiremez.
 *
 * <p>Saf fonksiyon: I/O yok, Spring yok.
 */
public final class TextChunker {

    /** Sınır araması parçanın en fazla bu oranı kadar geriye gider. */
    private static final double MIN_BOUNDARY_RATIO = 0.5;

    private TextChunker() {
    }

    public record TextChunk(int index, String content) {
    }

    public static List<TextChunk> chunk(String text, int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Parça boyutu pozitif olmalı.");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Örtüşme parça boyutundan küçük olmalı.");
        }

        String normalized = text == null ? "" : text.strip();

        if (normalized.isEmpty()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;

        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());

            if (end < normalized.length()) {
                end = boundaryBefore(normalized, start, end);
            }

            String content = normalized.substring(start, end).strip();

            if (!content.isEmpty()) {
                chunks.add(new TextChunk(index++, content));
            }

            if (end >= normalized.length()) {
                break;
            }

            start = Math.max(start + 1, end - overlap);
        }

        return List.copyOf(chunks);
    }

    /**
     * Parçayı paragraf, sonra cümle sınırında bitirmeye çalışır. Sınır çok
     * geride kalıyorsa (parçanın yarısından öncesi) sert kesme yapılır —
     * aksi hâlde tek bir uzun kelime sonsuz küçülmeye yol açardı.
     */
    private static int boundaryBefore(String text, int start, int end) {
        int floor = start + (int) (MIN_BOUNDARY_RATIO * (end - start));

        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph > floor) {
            return paragraph;
        }

        int sentence = text.lastIndexOf(". ", end);
        if (sentence > floor) {
            return sentence + 1;
        }

        int space = text.lastIndexOf(' ', end);
        if (space > floor) {
            return space;
        }

        return end;
    }
}
