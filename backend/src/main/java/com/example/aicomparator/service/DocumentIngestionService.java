package com.example.aicomparator.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.ai.EmbeddingProvider;
import com.example.aicomparator.dto.DocumentResponse;
import com.example.aicomparator.entity.Conversation;
import com.example.aicomparator.entity.Document;
import com.example.aicomparator.entity.DocumentChunk;
import com.example.aicomparator.repository.ConversationRepository;
import com.example.aicomparator.repository.DocumentChunkRepository;
import com.example.aicomparator.repository.DocumentRepository;

/**
 * Yükleme hattı: metni çıkar, parçala, göm, kaydet.
 *
 * <p>Tümü tek transaction: ya belge ve tüm parçaları birlikte yazılır ya
 * hiçbiri. Bu yüzden "yarım indekslenmiş belge" durumu ve onu izleyecek bir
 * durum kolonu yok.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIngestionService.class);

    private final ConversationRepository conversationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentTextExtractor textExtractor;
    private final EmbeddingProvider embeddingProvider;
    private final int chunkSizeChars;
    private final int chunkOverlapChars;

    public DocumentIngestionService(
            ConversationRepository conversationRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DocumentTextExtractor textExtractor,
            EmbeddingProvider embeddingProvider,
            @Value("${rag.chunk-size-chars}") int chunkSizeChars,
            @Value("${rag.chunk-overlap-chars}") int chunkOverlapChars
    ) {
        this.conversationRepository = conversationRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.textExtractor = textExtractor;
        this.embeddingProvider = embeddingProvider;
        this.chunkSizeChars = chunkSizeChars;
        this.chunkOverlapChars = chunkOverlapChars;
    }

    @Transactional
    public DocumentResponse ingest(
            Long conversationId,
            String filename,
            String contentType,
            byte[] bytes
    ) {
        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Konuşma bulunamadı."));

        String text = textExtractor.extract(filename, contentType, bytes);

        List<TextChunker.TextChunk> chunks =
                TextChunker.chunk(text, chunkSizeChars, chunkOverlapChars);

        if (chunks.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dosyadan indekslenecek metin çıkmadı."
            );
        }

        List<float[]> vectors = embed(
                chunks.stream().map(TextChunker.TextChunk::content).toList());

        Document document = documentRepository.save(new Document(
                conversation, filename, contentType, bytes.length,
                chunks.size()));

        List<DocumentChunk> entities = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            entities.add(new DocumentChunk(
                    document,
                    chunks.get(i).index(),
                    chunks.get(i).content(),
                    VectorMath.toBytes(vectors.get(i)),
                    embeddingProvider.modelName()
            ));
        }

        chunkRepository.saveAll(entities);

        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getSizeBytes(),
                document.getChunkCount(),
                document.getCreatedAt()
        );
    }

    private List<float[]> embed(List<String> texts) {
        try {
            return embeddingProvider.embedBatch(texts);
        } catch (RuntimeException exception) {
            log.warn("Embedding başarısız: {}", exception.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Belge indekslenemedi: embedding servisi yanıt vermedi."
            );
        }
    }
}
