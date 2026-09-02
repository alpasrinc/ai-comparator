package com.example.aicomparator.ai;

import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.aicomparator.dto.AiResult;
import com.example.aicomparator.dto.PromptParts;
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
            PromptParts prompt,
            ResponseIntensity intensity
    ) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens((int) intensity.scaleTokens(maxOutputTokens))
                .build();

        GenerateContentResponse response = client.models.generateContent(
                model,
                renderPrompt(prompt, intensity),
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
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(prompt, intensity, onToken,
                (int) intensity.scaleTokens(maxOutputTokens));
    }

    @Override
    public TokenUsage streamSynthesisMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(prompt, intensity, onToken,
                (int) intensity.scaleTokens(synthesisMaxOutputTokens));
    }

    /**
     * Sağlayıcıya gidecek nihai metin. Yoğunluk yönergesi yalnızca
     * değişken kuyruğa girer; prefix istekler arasında byte-byte aynı
     * kalmalıdır.
     */
    private String renderPrompt(
            PromptParts prompt,
            ResponseIntensity intensity
    ) {
        return prompt.cacheablePrefix()
                + intensity.applyTo(prompt.volatileSuffix());
    }

    private TokenUsage streamWithLimit(
            PromptParts prompt,
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
                                renderPrompt(prompt, intensity),
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

    /**
     * Gemini'de cache implicit çalışır; tek yapılan raporlanan okumayı
     * kaydetmek.
     *
     * <p>Dikkat: {@code promptTokenCount} cache'ten okunanları
     * <b>içerir</b>, Anthropic'in {@code input_tokens} alanı ise
     * içermez. {@link TokenUsage} tek bir anlam taşısın diye
     * (girdi = cache'lenmemiş kalan) burada çıkarılır.
     */
    private TokenUsage extractUsage(GenerateContentResponse response) {
        return response.usageMetadata()
                .map(meta -> {
                    long cachedTokens =
                            meta.cachedContentTokenCount().orElse(0);

                    return new TokenUsage(
                            Math.max(0, meta.promptTokenCount().orElse(0)
                                    - cachedTokens),
                            meta.candidatesTokenCount().orElse(0),
                            cachedTokens,
                            0
                    );
                })
                .orElse(TokenUsage.EMPTY);
    }

    @PreDestroy
    public void closeClient() {
        client.close();
    }
}
