package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.DebateRequest;
import com.example.aicomparator.entity.AiProviderType;

class DebateOrchestratorTests {

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void runsAllRoundsThenSynthesisAndPersists() throws Exception {
        AiProvider openAi = streamingProvider(AiProviderType.OPENAI, "A");
        AiProvider gemini = streamingProvider(AiProviderType.GEMINI, "B");

        DebateService debateService = mock(DebateService.class);
        when(debateService.createDebate(any())).thenReturn(42L);
        when(debateService.saveParticipantMessage(
                eq(42L), anyInt(), any(), any())).thenReturn(1L);
        when(debateService.saveSynthesisMessage(
                eq(42L), any(), any())).thenReturn(99L);

        DebateOrchestrator orchestrator = new DebateOrchestrator(
                List.of(openAi, gemini),
                executor,
                debateService,
                new DebatePromptBuilder(),
                new SseSupport(),
                5,
                10
        );

        DebateRequest request = new DebateRequest(
                "Konu",
                List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                2,
                AiProviderType.OPENAI
        );

        var emitter =
                new org.springframework.web.servlet.mvc.method.annotation
                        .SseEmitter(5000L);

        orchestrator.runDebate(request, emitter).get();

        ArgumentCaptor<Integer> roundCaptor =
                ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(debateService,
                        org.mockito.Mockito.times(4))
                .saveParticipantMessage(eq(42L), roundCaptor.capture(),
                        any(), any());
        assertThat(roundCaptor.getAllValues()).contains(1, 2);

        org.mockito.Mockito.verify(debateService)
                .saveSynthesisMessage(eq(42L), eq(AiProviderType.OPENAI), any());
        org.mockito.Mockito.verify(openAi, org.mockito.Mockito.times(2))
                .streamMessage(any(), any());
        org.mockito.Mockito.verify(openAi)
                .streamSynthesisMessage(any(), any());
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.times(2))
                .streamMessage(any(), any());
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.never())
                .streamSynthesisMessage(any(), any());
    }

    @Test
    void marksFailedWhenAllParticipantsFailInFirstRound() throws Exception {
        AiProvider openAi = failingStreamingProvider(AiProviderType.OPENAI);
        AiProvider gemini = failingStreamingProvider(AiProviderType.GEMINI);

        DebateService debateService = mock(DebateService.class);
        when(debateService.createDebate(any())).thenReturn(7L);

        DebateOrchestrator orchestrator = new DebateOrchestrator(
                List.of(openAi, gemini),
                executor,
                debateService,
                new DebatePromptBuilder(),
                new SseSupport(),
                5,
                10
        );

        DebateRequest request = new DebateRequest(
                "Konu",
                List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                2,
                AiProviderType.OPENAI
        );

        var emitter =
                new org.springframework.web.servlet.mvc.method.annotation
                        .SseEmitter(5000L);

        orchestrator.runDebate(request, emitter).get();

        org.mockito.Mockito.verify(debateService).markFailed(7L);
        org.mockito.Mockito.verify(debateService,
                        org.mockito.Mockito.never())
                .saveSynthesisMessage(any(), any(), any());
    }

    private AiProvider streamingProvider(
            AiProviderType type,
            String chunk
    ) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(type);
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(1);
            onToken.accept(chunk);
            return null;
        }).when(provider).streamMessage(any(), any());
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(1);
            onToken.accept(chunk);
            return null;
        }).when(provider).streamSynthesisMessage(any(), any());
        return provider;
    }

    private AiProvider failingStreamingProvider(AiProviderType type) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.getProviderType()).thenReturn(type);
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(provider).streamMessage(any(), any());
        return provider;
    }
}
