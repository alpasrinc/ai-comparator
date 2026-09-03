package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.ai.EmbeddingProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.dto.DocumentResponse;
import com.example.aicomparator.repository.DocumentChunkRepository;

@SpringBootTest
@Transactional
class DocumentIngestionServiceTests {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    @Test
    void storesChunksWithTheEmbeddingModelName() {
        when(embeddingProvider.modelName()).thenReturn("test-model");
        when(embeddingProvider.embedBatch(anyList()))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return texts.stream()
                            .map(text -> new float[] {1f, 0f, 0f})
                            .toList();
                });

        Long conversationId = newConversation();

        DocumentResponse response = ingestionService.ingest(
                conversationId,
                "not.txt",
                "text/plain",
                "Birinci cümle. İkinci cümle.".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(response.chunkCount()).isPositive();
        assertThat(chunkRepository.findByConversationId(conversationId))
                .hasSize(response.chunkCount())
                .allSatisfy(chunk -> {
                    assertThat(chunk.getEmbeddingModel()).isEqualTo("test-model");
                    assertThat(chunk.getEmbedding()).isNotEmpty();
                });
    }

    @Test
    void persistsNothingWhenEmbeddingFails() {
        when(embeddingProvider.modelName()).thenReturn("test-model");
        when(embeddingProvider.embedBatch(anyList()))
                .thenThrow(new IllegalStateException("embedding servisi kapalı"));

        Long conversationId = newConversation();

        assertThatThrownBy(() -> ingestionService.ingest(
                conversationId, "not.txt", "text/plain",
                "metin".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(chunkRepository.findByConversationId(conversationId))
                .isEmpty();
    }

    @Test
    void rejectsUnsupportedFileType() {
        Long conversationId = newConversation();

        assertThatThrownBy(() -> ingestionService.ingest(
                conversationId, "resim.png", "image/png", new byte[] {1, 2}))
                .isInstanceOf(ResponseStatusException.class);
    }

    private Long newConversation() {
        CompareResponse comparison = conversationService.saveComparison(
                "başlangıç",
                List.of(new AiResponse(null, "OPENAI", "cevap"))
        );
        return comparison.conversationId();
    }
}
