package com.example.aicomparator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.DebateDoneEvent;
import com.example.aicomparator.dto.DebateParticipantDoneEvent;
import com.example.aicomparator.dto.DebateParticipantErrorEvent;
import com.example.aicomparator.dto.DebateRequest;
import com.example.aicomparator.dto.DebateRoundDoneEvent;
import com.example.aicomparator.dto.DebateRoundStartEvent;
import com.example.aicomparator.dto.DebateStartEvent;
import com.example.aicomparator.dto.DebateSynthesisDoneEvent;
import com.example.aicomparator.dto.DebateTokenEvent;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.DebateStatus;

@Service
public class DebateOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(DebateOrchestrator.class);

    private final Map<AiProviderType, AiProvider> providersByType =
            new LinkedHashMap<>();
    private final ExecutorService aiExecutor;
    private final DebateService debateService;
    private final DebatePromptBuilder promptBuilder;
    private final SseSupport sseSupport;
    private final long requestTimeoutSeconds;
    private final long synthesisTimeoutSeconds;

    public DebateOrchestrator(
            List<AiProvider> providers,
            ExecutorService aiExecutor,
            DebateService debateService,
            DebatePromptBuilder promptBuilder,
            SseSupport sseSupport,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds,
            @Value("${ai.synthesis-timeout-seconds:90}")
            long synthesisTimeoutSeconds
    ) {
        for (AiProvider provider : providers) {
            providersByType.put(provider.getProviderType(), provider);
        }
        this.aiExecutor = aiExecutor;
        this.debateService = debateService;
        this.promptBuilder = promptBuilder;
        this.sseSupport = sseSupport;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.synthesisTimeoutSeconds = synthesisTimeoutSeconds;
    }

    public CompletableFuture<Void> runDebate(
            DebateRequest request,
            SseEmitter emitter
    ) {
        Long debateId = debateService.createDebate(request);
        Object lock = new Object();

        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> { });

        sseSupport.send(emitter, lock, "start",
                new DebateStartEvent(debateId));

        return CompletableFuture.runAsync(
                () -> execute(request, debateId, emitter, lock),
                aiExecutor
        );
    }

    private void execute(
            DebateRequest request,
            Long debateId,
            SseEmitter emitter,
            Object lock
    ) {
        ResponseIntensity intensity =
                ResponseIntensity.orDefault(request.intensity());
        try {
            List<Map<AiProviderType, String>> transcript = new ArrayList<>();

            for (int round = 1; round <= request.rounds(); round++) {
                sseSupport.send(emitter, lock, "round-start",
                        new DebateRoundStartEvent(round));

                Map<AiProviderType, String> roundResult = runRound(
                        request, debateId, round, transcript, intensity,
                        emitter, lock);
                transcript.add(roundResult);

                sseSupport.send(emitter, lock, "round-done",
                        new DebateRoundDoneEvent(round));

                boolean allBlank = roundResult.values().stream()
                        .allMatch(value -> value == null || value.isBlank());
                if (round == 1 && allBlank) {
                    debateService.markFailed(debateId);
                    sseSupport.send(emitter, lock, "done",
                            new DebateDoneEvent(debateId,
                                    DebateStatus.FAILED.name()));
                    return;
                }
            }

            runSynthesis(request, debateId, transcript, intensity,
                    emitter, lock);

            sseSupport.send(emitter, lock, "done",
                    new DebateDoneEvent(debateId,
                            DebateStatus.COMPLETED.name()));
        } catch (Exception exception) {
            log.warn("Münazara yürütülürken hata: {}",
                    exception.getMessage(), exception);
        } finally {
            synchronized (lock) {
                emitter.complete();
            }
        }
    }

    private Map<AiProviderType, String> runRound(
            DebateRequest request,
            Long debateId,
            int round,
            List<Map<AiProviderType, String>> transcript,
            ResponseIntensity intensity,
            SseEmitter emitter,
            Object lock
    ) {
        List<CompletableFuture<Map.Entry<AiProviderType, String>>> futures =
                new ArrayList<>();

        for (AiProviderType type : request.participants()) {
            AiProvider provider = resolveProvider(type);
            String prompt = round == 1
                    ? promptBuilder.buildFirstRoundPrompt(request.topic(), type)
                    : promptBuilder.buildCritiqueRoundPrompt(
                            request.topic(), type, transcript);

            futures.add(streamParticipant(
                    debateId, round, type, provider, prompt, intensity,
                    emitter, lock));
        }

        Map<AiProviderType, String> result = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<AiProviderType, String>> future
                : futures) {
            Map.Entry<AiProviderType, String> entry = future.join();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private CompletableFuture<Map.Entry<AiProviderType, String>>
            streamParticipant(
                    Long debateId,
                    int round,
                    AiProviderType type,
                    AiProvider provider,
                    String prompt,
                    ResponseIntensity intensity,
                    SseEmitter emitter,
                    Object lock
            ) {
        String name = type.name();
        AtomicReference<TokenUsage> usageRef =
                new AtomicReference<>(TokenUsage.EMPTY);

        return CompletableFuture.supplyAsync(() -> {
                    StringBuilder accumulated = new StringBuilder();
                    TokenUsage usage = provider.streamMessage(
                            prompt, intensity, delta -> {
                                accumulated.append(delta);
                                sseSupport.send(emitter, lock, "token",
                                        new DebateTokenEvent(round, name, delta));
                            });
                    usageRef.set(usage);
                    return accumulated.toString();
                }, aiExecutor)
                .orTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .handle((content, throwable) -> {
                    if (throwable == null && content != null
                            && !content.isBlank()) {
                        TokenUsage usage = usageRef.get();
                        Long messageId = debateService.saveParticipantMessage(
                                debateId, round, type, content, usage);
                        sseSupport.send(emitter, lock, "participant-done",
                                new DebateParticipantDoneEvent(
                                        round, name, messageId, content, usage));
                        return Map.entry(type, content);
                    }

                    log.warn("{} katılımcısı tur {} sırasında hata/boş cevap: {}",
                            name, round,
                            throwable == null ? "boş" : throwable.getMessage());
                    sseSupport.send(emitter, lock, "participant-error",
                            new DebateParticipantErrorEvent(round, name,
                                    "Bu yapay zekâdan yanıt alınamadı."));
                    return Map.entry(type, "");
                });
    }

    private void runSynthesis(
            DebateRequest request,
            Long debateId,
            List<Map<AiProviderType, String>> transcript,
            ResponseIntensity intensity,
            SseEmitter emitter,
            Object lock
    ) {
        AiProviderType synthType = request.synthesizer();
        AiProvider provider = resolveProvider(synthType);
        String name = synthType.name();
        String prompt = promptBuilder.buildSynthesisPrompt(
                request.topic(), transcript);

        StringBuilder accumulated = new StringBuilder();
        AtomicReference<TokenUsage> usageRef =
                new AtomicReference<>(TokenUsage.EMPTY);
        try {
            CompletableFuture.runAsync(() ->
                    usageRef.set(provider.streamSynthesisMessage(
                            prompt, intensity, delta -> {
                                accumulated.append(delta);
                                sseSupport.send(emitter, lock, "token",
                                        new DebateTokenEvent(0, name, delta));
                            })), aiExecutor)
                    .orTimeout(synthesisTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (Exception exception) {
            log.warn("Sentez sırasında hata: {}", exception.getMessage());
        }

        String content = accumulated.toString();
        if (content.isBlank()) {
            debateService.markCompletedWithoutSynthesis(debateId);
            sseSupport.send(emitter, lock, "participant-error",
                    new DebateParticipantErrorEvent(0, name,
                            "Ortak cevap üretilemedi. Tekrar deneyin."));
            return;
        }

        TokenUsage usage = usageRef.get();
        Long messageId = debateService.saveSynthesisMessage(
                debateId, synthType, content, usage);
        sseSupport.send(emitter, lock, "synthesis-done",
                new DebateSynthesisDoneEvent(name, messageId, content, usage));
    }

    private AiProvider resolveProvider(AiProviderType type) {
        AiProvider provider = providersByType.get(type);
        if (provider == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Desteklenmeyen AI sağlayıcısı: " + type);
        }
        return provider;
    }
}
