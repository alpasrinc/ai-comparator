package com.example.aicomparator.ai;

import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.aicomparator.dto.AiResult;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.entity.AiProviderType;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

@Service
public class GeminiProvider implements AiProvider {

    private final Client client;
    private final String model;
    private final int maxOutputTokens;
    private final int synthesisMaxOutputTokens;

    public GeminiProvider(
            @Value("${gemini.model}") String model,
            @Value("${gemini.max-output-tokens}") int maxOutputTokens,
            @Value("${gemini.synthesis-max-output-tokens}")
            int synthesisMaxOutputTokens
    ) {
        this.client = new Client();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.synthesisMaxOutputTokens = synthesisMaxOutputTokens;
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.GEMINI;
    }

    @Override
    public AiResult sendMessage(
            String userMessage,
            ResponseIntensity intensity
    ) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens((int) intensity.scaleTokens(maxOutputTokens))
                .build();

        GenerateContentResponse response = client.models.generateContent(
                model,
                intensity.applyTo(userMessage),
                config
        );

        String content = response.text();

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Gemini boş bir cevap döndürdü.");
        }

        return new AiResult(content, extractUsage(response));
    }

    @Override
    public TokenUsage streamMessage(
            String userMessage,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(userMessage, intensity, onToken,
                (int) intensity.scaleTokens(maxOutputTokens));
    }

    @Override
    public TokenUsage streamSynthesisMessage(
            String userMessage,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(userMessage, intensity, onToken,
                (int) intensity.scaleTokens(synthesisMaxOutputTokens));
    }

    private TokenUsage streamWithLimit(
            String userMessage,
            ResponseIntensity intensity,
            Consumer<String> onToken,
            int outputTokenLimit
    ) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens(outputTokenLimit)
                .build();

        TokenUsage usage = TokenUsage.EMPTY;

        try (
                ResponseStream<GenerateContentResponse> stream =
                        client.models.generateContentStream(
                                model,
                                intensity.applyTo(userMessage),
                                config
                        )
        ) {
            for (GenerateContentResponse chunk : stream) {
                String text = chunk.text();

                if (text != null && !text.isEmpty()) {
                    onToken.accept(text);
                }

                TokenUsage chunkUsage = extractUsage(chunk);
                if (chunkUsage.totalTokens() > 0) {
                    usage = chunkUsage;
                }
            }
        }

        return usage;
    }

    private TokenUsage extractUsage(GenerateContentResponse response) {
        return response.usageMetadata()
                .map(meta -> new TokenUsage(
                        meta.promptTokenCount().orElse(0),
                        meta.candidatesTokenCount().orElse(0)))
                .orElse(TokenUsage.EMPTY);
    }

    @PreDestroy
    public void closeClient() {
        client.close();
    }
}
