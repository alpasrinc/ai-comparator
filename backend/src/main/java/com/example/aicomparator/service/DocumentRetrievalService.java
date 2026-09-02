package com.example.aicomparator.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aicomparator.ai.EmbeddingProvider;
import com.example.aicomparator.dto.RetrievalResult;
import com.example.aicomparator.dto.RetrievedChunk;
import com.example.aicomparator.entity.DocumentChunk;
import com.example.aicomparator.repository.DocumentChunkRepository;

/**
 * Soruya en yakın belge parçalarını bulur.
 *
 * <p>Vektörler yazılırken normalize edildiği için benzerlik nokta çarpımına
 * eşit; ayrıca bir normalizasyon yapılmıyor.
 */
@Service
public class DocumentRetrievalService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentRetrievalService.class);

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingProvider embeddingProvider;
    private final boolean enabled;
    private final int topK;
    private final double minSimilarity;

    public DocumentRetrievalService(
            DocumentChunkRepository chunkRepository,
            EmbeddingProvider embeddingProvider,
            @Value("${rag.enabled}") boolean enabled,
            @Value("${rag.top-k}") int topK,
            @Value("${rag.min-similarity}") double minSimilarity
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingProvider = embeddingProvider;
        this.enabled = enabled;
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(Long conversationId, String question) {
        if (!enabled || conversationId == null) {
            return RetrievalResult.NONE;
        }

        List<DocumentChunk> chunks =
                chunkRepository.findByConversationId(conversationId);

        if (chunks.isEmpty()) {
            return RetrievalResult.NONE;
        }

        requireMatchingModel(chunks);

        float[] questionVector;

        try {
            questionVector = embeddingProvider.embed(question);
        } catch (RuntimeException exception) {
            log.warn("Soru gömülemedi, kaynaksız devam ediliyor: {}",
                    exception.getMessage());
            return RetrievalResult.temporarilyUnavailable();
        }

        List<RetrievedChunk> scored = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            double similarity = VectorMath.dot(
                    questionVector, VectorMath.fromBytes(chunk.getEmbedding()));

            if (similarity >= minSimilarity) {
                scored.add(new RetrievedChunk(
                        chunk.getId(),
                        chunk.getDocument().getId(),
                        chunk.getDocument().getFilename(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        similarity
                ));
            }
        }

        scored.sort(Comparator.comparingDouble(RetrievedChunk::similarity)
                .reversed());

        return new RetrievalResult(
                List.copyOf(scored.subList(0, Math.min(topK, scored.size()))),
                false
        );
    }

    /**
     * Model değişmişse eski vektörler anlamsızdır. Bu, düşerek devam
     * edilecek bir hata değil: çalışır görünen ama yanlış sonuç veren bir
     * sistem üretir.
     */
    private void requireMatchingModel(List<DocumentChunk> chunks) {
        String current = embeddingProvider.modelName();

        for (DocumentChunk chunk : chunks) {
            if (!current.equals(chunk.getEmbeddingModel())) {
                throw new IllegalStateException(
                        "Belgeler '" + chunk.getEmbeddingModel()
                                + "' modeliyle indekslenmiş, şu an '" + current
                                + "' kullanılıyor; belgeler yeniden yüklenmeli."
                );
            }
        }
    }
}
