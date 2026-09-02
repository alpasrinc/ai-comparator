package com.example.aicomparator.ai;

import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.example.aicomparator.dto.AiResult;
import com.example.aicomparator.dto.PromptParts;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.entity.AiProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.ResponseStreamEvent;

@Service
public class OpenAiProvider implements AiProvider {

    private final OpenAIClient client;
    private final String model;
    private final long maxOutputTokens;
    private final long synthesisMaxOutputTokens;

    public OpenAiProvider(
            @Value("${openai.model}") String model,
            @Value("${openai.max-output-tokens}") long maxOutputTokens,
            @Value("${openai.synthesis-max-output-tokens}")
            long synthesisMaxOutputTokens
    ) {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.synthesisMaxOutputTokens = synthesisMaxOutputTokens;
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.OPENAI;
    }

    @Override
    public AiResult sendMessage(
            PromptParts prompt,
            ResponseIntensity intensity
    ) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(renderPrompt(prompt, intensity))
                .maxOutputTokens(intensity.scaleTokens(maxOutputTokens))
                .build();

        Response response = client.responses().create(params);

        String content = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(outputMessage -> outputMessage.content().stream())
                .flatMap(outputContent -> outputContent.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining("\n"));

        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI boş bir cevap döndürdü.");
        }

        TokenUsage usage = response.usage()
                .map(OpenAiProvider::usageOf)
                .orElse(TokenUsage.EMPTY);

        return new AiResult(content, usage);
    }

    @Override
    public TokenUsage streamMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(prompt, intensity, onToken,
                intensity.scaleTokens(maxOutputTokens));
    }

    @Override
    public TokenUsage streamSynthesisMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(prompt, intensity, onToken,
                intensity.scaleTokens(synthesisMaxOutputTokens));
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
            long outputTokenLimit
    ) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(renderPrompt(prompt, intensity))
                .maxOutputTokens(outputTokenLimit)
                .build();

        AtomicReference<TokenUsage> usage =
                new AtomicReference<>(TokenUsage.EMPTY);

        try (
                StreamResponse<ResponseStreamEvent> stream =
                        client.responses().createStreaming(params)
        ) {
            stream.stream().forEach(event -> {
                event.outputTextDelta().ifPresent(
                        delta -> onToken.accept(delta.delta())
                );
                event.completed().ifPresent(done ->
                        done.response().usage().ifPresent(
                                u -> usage.set(usageOf(u))));
            });
        }

        return usage.get();
    }

    /**
     * OpenAI'da cache açık işaretleme gerektirmez; tek yapılan, raporlanan
     * cache okumasını kaydetmek.
     *
     * <p>Dikkat: OpenAI {@code inputTokens} değerine cache'ten okunanları
     * <b>dahil</b> eder, Anthropic ise etmez. {@link TokenUsage} tek bir
     * anlam taşısın diye (girdi = cache'lenmemiş kalan) burada çıkarılır.
     */
    private static TokenUsage usageOf(ResponseUsage usage) {
        long cachedTokens = usage.inputTokensDetails().cachedTokens();

        return new TokenUsage(
                Math.max(0, usage.inputTokens() - cachedTokens),
                usage.outputTokens(),
                cachedTokens,
                0
        );
    }

}
