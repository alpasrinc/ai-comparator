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

import com.example.aicomparator.ai.EmbeddingProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.dto.RetrievalResult;

@SpringBootTest
@Transactional
class DocumentRetrievalServiceTests {

    @Autowired
    private DocumentRetrievalService retrievalService;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private ConversationService conversationService;

    @MockitoBean
    private EmbeddingProvider embeddingProvider;

    @Test
    void returnsNothingWhenTheConversationHasNoDocuments() {
        when(embeddingProvider.modelName()).thenReturn("test-model");

        RetrievalResult result =
                retrievalService.retrieve(newConversation(), "soru");

        assertThat(result.chunks()).isEmpty();
        assertThat(result.unavailable()).isFalse();
    }

    @Test
    void ranksTheMostSimilarChunkFirst() {
        Long conversationId = indexTwoChunks();

        when(embeddingProvider.embed("soru"))
                .thenReturn(new float[] {1f, 0f, 0f});

        RetrievalResult result =
                retrievalService.retrieve(conversationId, "soru");

        assertThat(result.chunks()).isNotEmpty();
        assertThat(result.chunks().get(0).content()).contains("BIRINCI");
        assertThat(result.chunks())
                .isSortedAccordingTo((a, b) ->
                        Double.compare(b.similarity(), a.similarity()));
    }

    @Test
    void dropsChunksBelowTheSimilarityFloor() {
        Long conversationId = indexTwoChunks();

        when(embeddingProvider.embed("alakasız"))
                .thenReturn(new float[] {0f, 0f, 1f});

        RetrievalResult result =
                retrievalService.retrieve(conversationId, "alakasız");

        assertThat(result.chunks()).isEmpty();
        assertThat(result.unavailable()).isFalse();
    }

    @Test
    void degradesWhenEmbeddingFailsAtQueryTime() {
        Long conversationId = indexTwoChunks();

        when(embeddingProvider.embed("soru"))
                .thenThrow(new IllegalStateException("servis kapalı"));

        RetrievalResult result =
                retrievalService.retrieve(conversationId, "soru");

        assertThat(result.chunks()).isEmpty();
        assertThat(result.unavailable()).isTrue();
    }

    @Test
    void failsLoudlyWhenStoredChunksUseAnotherEmbeddingModel() {
        Long conversationId = indexTwoChunks();

        when(embeddingProvider.modelName()).thenReturn("baska-model");
        when(embeddingProvider.embed("soru"))
                .thenReturn(new float[] {1f, 0f, 0f});

        assertThatThrownBy(() -> retrievalService.retrieve(conversationId, "soru"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yeniden yüklenmeli");
    }

    private Long indexTwoChunks() {
        when(embeddingProvider.modelName()).thenReturn("test-model");
        when(embeddingProvider.embedBatch(anyList()))
                .thenReturn(List.of(
                        new float[] {1f, 0f, 0f},
                        new float[] {0f, 1f, 0f}));

        Long conversationId = newConversation();

        ingestionService.ingest(
                conversationId, "not.txt", "text/plain",
                ("BIRINCI parca metni.\n\n" + "x".repeat(1200)
                        + "\n\nIKINCI parca metni.")
                        .getBytes(StandardCharsets.UTF_8));

        return conversationId;
    }

    private Long newConversation() {
        CompareResponse comparison = conversationService.saveComparison(
                "başlangıç",
                List.of(new AiResponse(null, "OPENAI", "cevap"))
        );
        return comparison.conversationId();
    }
}
