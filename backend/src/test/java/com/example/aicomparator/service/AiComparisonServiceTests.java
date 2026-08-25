package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.AiResponse;
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

    @Test
    void shouldWrapSingleProviderCallWithTimeoutProtection() {
        AiProvider slowProvider = provider(
                AiProviderType.OPENAI,
                "Geç gelen cevap"
        );
        ConversationService conversationService =
                mock(ConversationService.class);

        AiComparisonService service = new AiComparisonService(
                List.of(slowProvider),
                executor,
                conversationService,
                0
        );

        AiResponse response = service.sendSingle(
                AiProviderType.OPENAI,
                "Merhaba"
        );

        assertThat(response.provider()).isEqualTo("OPENAI");
        assertThat(response.content()).isNull();
        assertThat(response.error()).contains("zaman aşımına");
    }

    @Test
    void shouldReturnFailureResponseWhenSingleProviderThrows() {
        AiProvider failing = failingProvider(AiProviderType.ANTHROPIC);
        ConversationService conversationService =
                mock(ConversationService.class);

        AiComparisonService service = new AiComparisonService(
                List.of(failing),
                executor,
                conversationService,
                5
        );

        AiResponse response = service.sendSingle(
                AiProviderType.ANTHROPIC,
                "Merhaba"
        );

        assertThat(response.provider()).isEqualTo("ANTHROPIC");
        assertThat(response.content()).isNull();
        assertThat(response.error()).isNotBlank();
    }

    @Test
    void shouldStreamTokensAndPersistAssistantMessageOnCompletion()
            throws Exception {
        AiProvider streamingProvider = streamingProvider(
                AiProviderType.OPENAI,
                "Mer",
                "haba"
        );
        ConversationService conversationService =
                mock(ConversationService.class);

        when(conversationService.startComparison("Selam"))
                .thenReturn(new ConversationService.UserTurnResult(1L, 2L));

        CountDownLatch latch = new CountDownLatch(1);

        when(
                conversationService.saveRetriedResponse(
                        eq(1L),
                        eq(2L),
                        any()
                )
        ).thenAnswer(invocation -> {
            AiResponse response = invocation.getArgument(2);
            latch.countDown();
            return AiResponse.success(3L, response.provider(), response.content());
        });

        AiComparisonService service = new AiComparisonService(
                List.of(streamingProvider),
                executor,
                conversationService,
                5
        );

        SseEmitter emitter = new SseEmitter(2000L);
        service.streamCompare(null, "Selam", emitter);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        ArgumentCaptor<AiResponse> captor =
                ArgumentCaptor.forClass(AiResponse.class);
        verify(conversationService)
                .saveRetriedResponse(eq(1L), eq(2L), captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("Merhaba");
    }

    @Test
    void shouldNotPersistWhenStreamingProviderThrows() {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(AiProviderType.ANTHROPIC);
        doThrow(new IllegalStateException("boom"))
                .when(provider)
                .streamMessage(anyString(), any());

        ConversationService conversationService =
                mock(ConversationService.class);

        when(conversationService.startComparison("Selam"))
                .thenReturn(new ConversationService.UserTurnResult(1L, 2L));

        AiComparisonService service = new AiComparisonService(
                List.of(provider),
                executor,
                conversationService,
                5
        );

        SseEmitter emitter = new SseEmitter(2000L);
        service.streamCompare(null, "Selam", emitter);

        verify(conversationService, after(500).never())
                .saveRetriedResponse(any(), any(), any());
    }

    private AiProvider streamingProvider(
            AiProviderType providerType,
            String... chunks
    ) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(providerType);

        doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(1);

            for (String chunk : chunks) {
                onToken.accept(chunk);
            }

            return null;
        }).when(provider).streamMessage(anyString(), any());

        return provider;
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
