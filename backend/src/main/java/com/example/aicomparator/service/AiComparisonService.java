package com.example.aicomparator.service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;

@Service
public class AiComparisonService {

    private final List<AiProvider> providers;
    private final ExecutorService aiExecutor;
    private final ConversationService conversationService;

    public AiComparisonService(
            List<AiProvider> providers,
            ExecutorService aiExecutor,
            ConversationService conversationService
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(AiProvider::getProviderType))
                .toList();

        this.aiExecutor = aiExecutor;
        this.conversationService = conversationService;
    }

    public CompareResponse compare(String userMessage) {
        List<CompletableFuture<AiResponse>> responseFutures =
                providers.stream()
                        .map(provider -> CompletableFuture.supplyAsync(
                                () -> new AiResponse(
                                        null,
                                        provider.getProviderType().name(),
                                        provider.sendMessage(userMessage)
                                ),
                                aiExecutor
                        ))
                        .toList();

        List<AiResponse> responses = responseFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        return conversationService.saveComparison(
                userMessage,
                responses
        );
    }
}