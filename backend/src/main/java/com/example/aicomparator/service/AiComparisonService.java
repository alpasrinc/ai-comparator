package com.example.aicomparator.service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.entity.AiProviderType;

@Service
public class AiComparisonService {

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
        String providerPrompt = conversationId == null
                ? userMessage
                : conversationService.buildActiveContextPrompt(
                        conversationId,
                        userMessage
                );

        List<CompletableFuture<AiResponse>> responseFutures =
                providers.stream()
                        .map(provider -> requestProvider(
                                provider,
                                providerPrompt
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
        AiProvider provider = providers.stream()
                .filter(candidate ->
                        candidate.getProviderType() == providerType
                )
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Desteklenmeyen AI sağlayıcısı."
                ));

        String prompt = conversationService.buildPromptForUserMessage(
                conversationId,
                userMessageId
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
                .exceptionally(exception -> AiResponse.failure(
                        providerName,
                        "Bu yapay zekâdan yanıt alınamadı. Tekrar deneyin."
                ));
    }
}
