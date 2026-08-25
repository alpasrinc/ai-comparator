package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.entity.AiProviderType;

class AiComparisonServiceTests {

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void shouldKeepSuccessfulResponsesWhenOneProviderFails() {
        AiProvider openAi = provider(
                AiProviderType.OPENAI,
                "OpenAI cevabı"
        );
        AiProvider anthropic = failingProvider(AiProviderType.ANTHROPIC);
        AiProvider gemini = provider(
                AiProviderType.GEMINI,
                "Gemini cevabı"
        );
        ConversationService conversationService =
                mock(ConversationService.class);

        when(conversationService.saveComparison(eq("Merhaba"), anyList()))
                .thenAnswer(invocation -> new CompareResponse(
                        1L,
                        2L,
                        invocation.getArgument(1)
                ));

        AiComparisonService service = new AiComparisonService(
                List.of(openAi, anthropic, gemini),
                executor,
                conversationService,
                5
        );

        CompareResponse result = service.compare(null, "Merhaba");

        assertThat(result.responses())
                .hasSize(3)
                .filteredOn(response -> response.error() != null)
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.provider()).isEqualTo("ANTHROPIC");
                    assertThat(response.content()).isNull();
                });

        assertThat(result.responses())
                .filteredOn(response -> response.error() == null)
                .extracting(response -> response.provider())
                .containsExactly("OPENAI", "GEMINI");
    }

    @Test
    void shouldReturnTimeoutErrorForSlowProvider() {
        AiProvider slowProvider = provider(
                AiProviderType.OPENAI,
                "Geç gelen cevap"
        );
        ConversationService conversationService =
                mock(ConversationService.class);

        when(conversationService.saveComparison(eq("Merhaba"), anyList()))
                .thenAnswer(invocation -> new CompareResponse(
                        1L,
                        2L,
                        invocation.getArgument(1)
                ));

        AiComparisonService service = new AiComparisonService(
                List.of(slowProvider),
                executor,
                conversationService,
                0
        );

        CompareResponse result = service.compare(null, "Merhaba");

        assertThat(result.responses())
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.provider()).isEqualTo("OPENAI");
                    assertThat(response.content()).isNull();
                    assertThat(response.error()).contains("zaman aşımına");
                });
    }

    private AiProvider provider(
            AiProviderType providerType,
            String response
    ) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(providerType);
        when(provider.sendMessage("Merhaba")).thenReturn(response);
        return provider;
    }

    private AiProvider failingProvider(AiProviderType providerType) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(providerType);
        when(provider.sendMessage("Merhaba"))
                .thenThrow(new IllegalStateException("API unavailable"));
        return provider;
    }
}
