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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
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
    private final long requestTimeoutSeconds;

    public AiComparisonService(
            List<AiProvider> providers,
            ExecutorService aiExecutor,
            ConversationService conversationService,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(AiProvider::getProviderType))
                .toList();
        this.aiExecutor = aiExecutor;
        this.conversationService = conversationService;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public CompareResponse compare(
            Long conversationId,
            String userMessage
    ) {
        List<CompletableFuture<AiResponse>> responseFutures =
                providers.stream()
                        .map(provider -> requestProvider(
                                provider,
                                conversationId == null
                                        ? userMessage
                                        : conversationService.buildActiveContextPrompt(
                                                conversationId,
                                                userMessage,
                                                provider.getProviderType()
                                        )
                        ))
                        .toList();

        List<AiResponse> responses = responseFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        if (conversationId == null) {
            return conversationService.saveComparison(
                    userMessage,
                    responses
            );
        }

        return conversationService.saveContinuation(
                conversationId,
                userMessage,
                responses
        );
    }

    public AiResponse retryProvider(
            Long conversationId,
            Long userMessageId,
            AiProviderType providerType
    ) {
        AiProvider provider = resolveProvider(providerType);

        String prompt = conversationService.buildPromptForUserMessage(
                conversationId,
                userMessageId,
                providerType
        );

        AiResponse response = requestProvider(provider, prompt).join();

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
        AiProvider provider = resolveProvider(providerType);

        return requestProvider(provider, message).join();
    }

    public void streamCompare(
            Long conversationId,
            String userMessage,
            SseEmitter emitter
    ) {
        ConversationService.UserTurnResult turn = conversationId == null
                ? conversationService.startComparison(userMessage)
                : conversationService.startContinuation(
                        conversationId,
                        userMessage
                );

        Object emitterLock = new Object();

        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> { });

        sendEvent(
                emitter,
                emitterLock,
                "start",
                new StreamStartEvent(turn.conversationId(), turn.userMessageId())
        );

        AtomicInteger remaining = new AtomicInteger(providers.size());

        for (AiProvider provider : providers) {
            String providerPrompt = conversationId == null
                    ? userMessage
                    : conversationService.buildActiveContextPrompt(
                            turn.conversationId(),
                            userMessage,
                            provider.getProviderType()
                    );

            streamProvider(provider, providerPrompt, turn, emitter, emitterLock, remaining);
        }
    }

    private void streamProvider(
            AiProvider provider,
            String prompt,
            ConversationService.UserTurnResult turn,
            SseEmitter emitter,
            Object emitterLock,
            AtomicInteger remaining
    ) {
        String providerName = provider.getProviderType().name();
        StringBuilder accumulated = new StringBuilder();

        CompletableFuture
                .runAsync(
                        () -> provider.streamMessage(prompt, delta -> {
                            accumulated.append(delta);
                            sendEvent(
                                    emitter,
                                    emitterLock,
                                    "token",
                                    new StreamTokenEvent(providerName, delta)
                            );
                        }),
                        aiExecutor
                )
                .orTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((ignoredValue, throwable) -> {
                    if (throwable == null) {
                        String content = accumulated.toString();

                        if (content.isBlank()) {
                            log.warn(
                                    "{} sağlayıcısı streaming sırasında boş cevap döndürdü.",
                                    providerName
                            );

                            sendEvent(
                                    emitter,
                                    emitterLock,
                                    "error",
                                    new StreamErrorEvent(
                                            providerName,
                                            "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin."
                                    )
                            );
                        } else {
                            AiResponse saved = conversationService.saveRetriedResponse(
                                    turn.conversationId(),
                                    turn.userMessageId(),
                                    AiResponse.success(null, providerName, content)
                            );

                            sendEvent(
                                    emitter,
                                    emitterLock,
                                    "done",
                                    new StreamDoneEvent(
                                            providerName,
                                            saved.messageId(),
                                            saved.content()
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

                        sendEvent(
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

    private void sendEvent(
            SseEmitter emitter,
            Object emitterLock,
            String eventName,
            Object data
    ) {
        synchronized (emitterLock) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(eventName)
                                .data(data, MediaType.APPLICATION_JSON)
                );
            } catch (Exception exception) {
                log.debug(
                        "SSE gönderimi başarısız (istemci muhtemelen "
                                + "bağlantıyı kapattı): {}",
                        exception.getMessage()
                );
            }
        }
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

    private CompletableFuture<AiResponse> requestProvider(
            AiProvider provider,
            String prompt
    ) {
        String providerName = provider.getProviderType().name();

        return CompletableFuture.supplyAsync(
                        () -> AiResponse.success(
                                null,
                                providerName,
                                provider.sendMessage(prompt)
                        ),
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
