package com.example.aicomparator.ai;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.aicomparator.entity.AiProviderType;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

@Service
public class GeminiProvider implements AiProvider {

    private final Client client;
    private final String model;
    private final int maxOutputTokens;

    public GeminiProvider(
            @Value("${gemini.model}") String model,
            @Value("${gemini.max-output-tokens}") int maxOutputTokens
    ) {
        this.client = new Client();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
public AiProviderType getProviderType() {
    return AiProviderType.GEMINI;
}
    @Override
    public String sendMessage(String userMessage) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();

        GenerateContentResponse response = client.models.generateContent(
                model,
                userMessage,
                config
        );

        String content = response.text();

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Gemini boş bir cevap döndürdü.");
        }

        return content;
    }

    @PreDestroy
    public void closeClient() {
        client.close();
    }
}