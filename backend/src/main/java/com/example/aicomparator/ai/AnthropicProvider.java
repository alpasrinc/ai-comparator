package com.example.aicomparator.ai;

import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.aicomparator.dto.AiResult;
import com.example.aicomparator.dto.PromptParts;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.entity.AiProviderType;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;

@Service
public class AnthropicProvider implements AiProvider  {

    private final AnthropicClient client;
    private final String model;
    private final long maxOutputTokens;
    private final long synthesisMaxOutputTokens;

    public AnthropicProvider(
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-output-tokens}") long maxOutputTokens,
            @Value("${anthropic.synthesis-max-output-tokens}")
            long synthesisMaxOutputTokens
    ) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.synthesisMaxOutputTokens = synthesisMaxOutputTokens;
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.ANTHROPIC;
    }

    @Override
    public AiResult sendMessage(
            PromptParts prompt,
            ResponseIntensity intensity
    ) {
        MessageCreateParams params = createParams(
                renderPrompt(prompt, intensity),
                intensity.scaleTokens(maxOutputTokens));

        Message response = client.messages().create(params);

        String content = response.content().stream()
                .flatMap(contentBlock -> contentBlock.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        if (content.isBlank()) {
            throw new IllegalStateException("Anthropic boş bir cevap döndürdü.");
        }

        TokenUsage usage = new TokenUsage(
                response.usage().inputTokens(),
                response.usage().outputTokens());

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

    private TokenUsage streamWithLimit(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken,
            long outputTokenLimit
    ) {
        MessageCreateParams params = createParams(
                renderPrompt(prompt, intensity), outputTokenLimit);

        AtomicLong inputTokens = new AtomicLong(0);
        AtomicLong outputTokens = new AtomicLong(0);

        try (
                StreamResponse<RawMessageStreamEvent> stream =
                        client.messages().createStreaming(params)
        ) {
            stream.stream().forEach(event -> {
                event.contentBlockDelta().ifPresent(blockDelta ->
                        blockDelta.delta().text().ifPresent(
                                textDelta -> onToken.accept(textDelta.text())
                        )
                );
                event.messageStart().ifPresent(start ->
                        inputTokens.set(start.message().usage().inputTokens()));
                event.messageDelta().ifPresent(delta ->
                        outputTokens.set(delta.usage().outputTokens()));
            });
        }

        return new TokenUsage(inputTokens.get(), outputTokens.get());
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

    private MessageCreateParams createParams(
            String renderedPrompt,
            long outputTokenLimit
    ) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(outputTokenLimit)
                .addUserMessage(renderedPrompt)
                .build();
    }
}
