package com.example.aicomparator.service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.PromptParts;
import com.example.aicomparator.dto.AiResult;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.RetrievalResult;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.dto.StreamDoneEvent;
import com.example.aicomparator.dto.StreamErrorEvent;
import com.example.aicomparator.dto.StreamStartEvent;
import com.example.aicomparator.dto.StreamTokenEvent;
import com.example.aicomparator.entity.AiProviderType;

@Service
public class AiComparisonService {

    private static final Logger log =
            LoggerFactory.getLogger(AiComparisonService.class);

    private final List<AiProvider> providers;
    private final ExecutorService aiExecutor;
    private final ConversationService conversationService;
    private final DocumentRetrievalService retrievalService;
    private final SseSupport sseSupport;
    private final long requestTimeoutSeconds;

    public AiComparisonService(
            List<AiProvider> providers,
            ExecutorService aiExecutor,
            ConversationService conversationService,
            DocumentRetrievalService retrievalService,
            SseSupport sseSupport,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(AiProvider::getProviderType))
                .toList();
        this.aiExecutor = aiExecutor;
        this.conversationService = conversationService;
        this.retrievalService = retrievalService;
        this.sseSupport = sseSupport;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public CompareResponse compare(
            Long conversationId,
            String userMessage
    ) {
        return compare(conversationId, userMessage, null,
                ResponseIntensity.MEDIUM);
    }

    public CompareResponse compare(
            Long conversationId,
            String userMessage,
            List<AiProviderType> requestedProviderTypes
    ) {
        return compare(conversationId, userMessage, requestedProviderTypes,
                ResponseIntensity.MEDIUM);
    }

    public CompareResponse compare(
            Long conversationId,
            String userMessage,
            List<AiProviderType> requestedProviderTypes,
            ResponseIntensity intensity
    ) {
        ResponseIntensity effectiveIntensity =
                ResponseIntensity.orDefault(intensity);
        List<AiProvider> selectedProviders = selectProviders(
                requestedProviderTypes
        );
        RetrievalResult retrieval =
                retrievalService.retrieve(conversationId, userMessage);

        List<CompletableFuture<AiResponse>> responseFutures =
                selectedProviders.stream()
                        .map(provider -> requestProvider(
                                provider,
                                conversationId == null
                                        ? PromptParts.volatileOnly(
                                                userMessage)
                                        : conversationService.buildActiveContextPrompt(
                                                conversationId,
                                                userMessage,
                                                provider.getProviderType(),
                                                retrieval.chunks()
                                        ),
                                effectiveIntensity
                        ))
                        .toList();

        List<AiResponse> responses = responseFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        CompareResponse saved = conversationId == null
                ? conversationService.saveComparison(userMessage, responses)
                : conversationService.saveContinuation(
                        conversationId, userMessage, responses);

        conversationService.saveSources(
                saved.userMessageId(), retrieval.chunks());

        return new CompareResponse(
                saved.conversationId(),
                saved.userMessageId(),
                saved.responses(),
                retrieval.chunks(),
                retrieval.unavailable()
        );
    }

    public AiResponse retryProvider(
            Long conversationId,
            Long userMessageId,
            AiProviderType providerType
    ) {
        AiProvider provider = resolveProvider(providerType);

        PromptParts prompt = conversationService.buildPromptForUserMessage(
                conversationId,
                userMessageId,
                providerType
        );

        AiResponse response = requestProvider(
                provider, prompt, ResponseIntensity.MEDIUM).join();

        if (response.error() != null) {
            return response;
        }

        return conversationService.saveRetriedResponse(
                conversationId,
                userMessageId,
                response
        );
    }

    public AiResponse sendSingle(
            AiProviderType providerType,
            String message
    ) {
        return sendSingle(providerType, message, ResponseIntensity.MEDIUM);
    }

    public AiResponse sendSingle(
            AiProviderType providerType,
            String message,
            ResponseIntensity intensity
    ) {
        AiProvider provider = resolveProvider(providerType);

        return requestProvider(
                provider,
                PromptParts.volatileOnly(message),
                ResponseIntensity.orDefault(intensity)
        ).join();
    }

    public void streamCompare(
            Long conversationId,
            String userMessage,
            SseEmitter emitter
    ) {
        streamCompare(conversationId, userMessage, null,
                ResponseIntensity.MEDIUM, emitter);
    }

    public void streamCompare(
            Long conversationId,
            String userMessage,
            List<AiProviderType> requestedProviderTypes,
            SseEmitter emitter
    ) {
        streamCompare(conversationId, userMessage, requestedProviderTypes,
                ResponseIntensity.MEDIUM, emitter);
    }

    public void streamCompare(
            Long conversationId,
            String userMessage,
            List<AiProviderType> requestedProviderTypes,
            ResponseIntensity intensity,
            SseEmitter emitter
    ) {
        ResponseIntensity effectiveIntensity =
                ResponseIntensity.orDefault(intensity);
        List<AiProvider> selectedProviders = selectProviders(
                requestedProviderTypes
        );

        ConversationService.UserTurnResult turn = conversationId == null
                ? conversationService.startComparison(userMessage)
                : conversationService.startContinuation(
                        conversationId,
                        userMessage
                );

        RetrievalResult retrieval = retrievalService.retrieve(
                turn.conversationId(), userMessage);

        conversationService.saveSources(
                turn.userMessageId(), retrieval.chunks());

        Object emitterLock = new Object();

        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> { });

        sseSupport.send(
                emitter,
                emitterLock,
                "start",
                new StreamStartEvent(
                        turn.conversationId(),
                        turn.userMessageId(),
                        retrieval.chunks(),
                        retrieval.unavailable())
        );

        AtomicInteger remaining = new AtomicInteger(selectedProviders.size());

        for (AiProvider provider : selectedProviders) {
            PromptParts providerPrompt = conversationId == null
                    ? PromptParts.volatileOnly(userMessage)
                    : conversationService.buildActiveContextPrompt(
                            turn.conversationId(),
                            userMessage,
                            provider.getProviderType(),
                            retrieval.chunks()
                    );

            streamProvider(provider, providerPrompt, turn, effectiveIntensity,
                    emitter, emitterLock, remaining);
        }
    }

    private void streamProvider(
            AiProvider provider,
            PromptParts prompt,
            ConversationService.UserTurnResult turn,
            ResponseIntensity intensity,
            SseEmitter emitter,
            Object emitterLock,
            AtomicInteger remaining
    ) {
        String providerName = provider.getProviderType().name();
        StringBuilder accumulated = new StringBuilder();

        CompletableFuture
                .supplyAsync(
                        () -> provider.streamMessage(prompt, intensity, delta -> {
                            accumulated.append(delta);
                            sseSupport.send(
                                    emitter,
                                    emitterLock,
                                    "token",
                                    new StreamTokenEvent(providerName, delta)
                            );
                        }),
                        aiExecutor
                )
                .orTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((usage, throwable) -> {
                    if (throwable == null) {
                        String content = accumulated.toString();

                        if (content.isBlank()) {
                            log.warn(
                                    "{} sağlayıcısı streaming sırasında boş cevap döndürdü.",
                                    providerName
                            );

                            sseSupport.send(
                                    emitter,
                                    emitterLock,
                                    "error",
                                    new StreamErrorEvent(
                                            providerName,
                                            "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin."
                                    )
                            );
                        } else {
                            TokenUsage resolvedUsage =
                                    usage == null ? TokenUsage.EMPTY : usage;
                            AiResponse saved = conversationService.saveRetriedResponse(
                                    turn.conversationId(),
                                    turn.userMessageId(),
                                    AiResponse.success(null, providerName,
                                            content, resolvedUsage)
                            );

                            sseSupport.send(
                                    emitter,
                                    emitterLock,
                                    "done",
                                    new StreamDoneEvent(
                                            providerName,
                                            saved.messageId(),
                                            saved.content(),
                                            saved.usage()
                                    )
                            );
                        }
                    } else {
                        Throwable cause = throwable instanceof CompletionException
                                ? throwable.getCause()
                                : throwable;

                        String errorMessage = cause instanceof TimeoutException
                                ? "Yanıt zaman aşımına uğradı. Tekrar deneyin."
                                : "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin.";

                        log.warn(
                                "{} sağlayıcısından streaming yanıtı alınamadı: {}",
                                providerName,
                                cause.getMessage(),
                                cause
                        );

                        sseSupport.send(
                                emitter,
                                emitterLock,
                                "error",
                                new StreamErrorEvent(providerName, errorMessage)
                        );
                    }

                    if (remaining.decrementAndGet() == 0) {
                        synchronized (emitterLock) {
                            emitter.complete();
                        }
                    }
                });
    }

    private AiProvider resolveProvider(AiProviderType providerType) {
        return providers.stream()
                .filter(candidate ->
                        candidate.getProviderType() == providerType
                )
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Desteklenmeyen AI sağlayıcısı."
                ));
    }

    private List<AiProvider> selectProviders(
            List<AiProviderType> requestedProviderTypes
    ) {
        if (requestedProviderTypes == null) {
            return providers;
        }

        if (requestedProviderTypes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "En az bir AI seçilmelidir."
            );
        }

        List<AiProvider> selectedProviders = providers.stream()
                .filter(provider -> requestedProviderTypes.contains(
                        provider.getProviderType()
                ))
                .toList();

        if (selectedProviders.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Geçerli bir AI seçilmelidir."
            );
        }

        return selectedProviders;
    }

    private CompletableFuture<AiResponse> requestProvider(
            AiProvider provider,
            PromptParts prompt,
            ResponseIntensity intensity
    ) {
        String providerName = provider.getProviderType().name();

        return CompletableFuture.supplyAsync(
                        () -> {
                            AiResult result =
                                    provider.sendMessage(prompt, intensity);
                            return AiResponse.success(
                                    null,
                                    providerName,
                                    result.content(),
                                    result.usage()
                            );
                        },
                        aiExecutor
                )
                .completeOnTimeout(
                        AiResponse.failure(
                                providerName,
                                "Yanıt zaman aşımına uğradı. Tekrar deneyin."
                        ),
                        requestTimeoutSeconds,
                        TimeUnit.SECONDS
                )
                .exceptionally(exception -> {
                    log.warn(
                            "{} sağlayıcısından yanıt alınamadı: {}",
                            providerName,
                            exception.getMessage(),
                            exception
                    );

                    return AiResponse.failure(
                            providerName,
                            "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin."
                    );
                });
    }
}
