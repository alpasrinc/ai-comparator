package com.example.aicomparator.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.aicomparator.ai.AiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;

@Service
public class AiComparisonService {

    private final List<AiProvider> providers;

    public AiComparisonService(List<AiProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(AiProvider::getProviderType))
                .toList();
    }

    public CompareResponse compare(String userMessage) {
        List<AiResponse> responses = providers.stream()
                .map(provider -> new AiResponse(
                        provider.getProviderType().name(),
                        provider.sendMessage(userMessage)
                ))
                .toList();

        return new CompareResponse(responses);
    }
}