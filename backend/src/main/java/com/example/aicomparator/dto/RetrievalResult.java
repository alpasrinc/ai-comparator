package com.example.aicomparator.dto;

import java.util.List;

/**
 * @param chunks      eşiği geçen parçalar, en benzer önce
 * @param unavailable retrieval geçici bir hata yüzünden yapılamadı; sohbet
 *                    kaynaksız devam etti. Sessiz düşüşü görünür kılar.
 */
public record RetrievalResult(List<RetrievedChunk> chunks, boolean unavailable) {

    public static final RetrievalResult NONE =
            new RetrievalResult(List.of(), false);

    public static RetrievalResult temporarilyUnavailable() {
        return new RetrievalResult(List.of(), true);
    }
}
