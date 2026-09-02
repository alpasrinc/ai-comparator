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
        // Örtüşme, boundaryBefore'un garanti ettiği alt sınırdan (parçanın
        // yarısı) büyük veya eşit olursa "end - overlap" başlangıcın gerisine
        // düşebilir ve döngü her seferinde tek karakter ilerler — parça
        // sayısı yüzlerce katına çıkar, gömme faturası sessizce patlar.
        if (overlap < 0 || overlap >= chunkSize * MIN_BOUNDARY_RATIO) {
            throw new IllegalArgumentException(
                    "Örtüşme, ilerlemeyi durdurmayacak kadar küçük olmalı "
                            + "(parça boyutunun yarısından az).");
        }

        // CRLF'yi LF'e indirger: "\r\n\r\n" harfi harfine "\n\n" içermez, bu
        // yüzden paragraf katmanı (en kaliteli sınır) CRLF metinlerde
        // sessizce hiç devreye girmezdi. Bu makine Windows olduğundan ve
        // depo CRLF sakladığından bu, varsayılan durumdur.
        String normalized = text == null ? ""
                : text.replace("\r\n", "\n").replace("\r", "\n").strip();

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
     * Parçayı paragraf, sonra cümle, sonra tek satır sonu sınırında
     * bitirmeye çalışır. Sınır çok geride kalıyorsa (parçanın yarısından
     * öncesi) sert kesme yapılır — aksi hâlde tek bir uzun kelime sonsuz
     * küçülmeye yol açardı.
     */
    private static int boundaryBefore(String text, int start, int end) {
        int floor = start + (int) (MIN_BOUNDARY_RATIO * (end - start));

        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph > floor) {
            return paragraph;
        }

        // fromIndex = end - 1: lastIndexOf(String, int) eşleşmeyi fromIndex'te
        // de başlatabilir. end'den arasak "sentence" end'e eşit çıkabilir ve
        // aşağıdaki +1 pencerenin bir karakter dışına taşardı.
        int sentence = text.lastIndexOf(". ", end - 1);
        if (sentence > floor) {
            return sentence + 1;
        }

        // PDF metin çıkarımı genelde paragraf arasına boş satır koymaz, her
        // satırı tek "\n" ile ayırır — bu yüzden paragraf katmanı PDF'lerde
        // nadiren devreye girer. Tek satır sonu, kelime ortasından daha iyi
        // ama cümle sonundan daha zayıf bir sınırdır.
        int newline = text.lastIndexOf('\n', end);
        if (newline > floor) {
            return newline;
        }

        int space = text.lastIndexOf(' ', end);
        if (space > floor) {
            return space;
        }

        return end;
    }
}
