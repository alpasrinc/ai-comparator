package com.example.aicomparator.ai;

import java.util.List;
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
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;

@Service
public class AnthropicProvider implements AiProvider  {

    private static final String TTL_ONE_HOUR = "1h";

    private final AnthropicClient client;
    private final String model;
    private final long maxOutputTokens;
    private final long synthesisMaxOutputTokens;
    private final CacheControlEphemeral cacheControl;

    public AnthropicProvider(
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-output-tokens}") long maxOutputTokens,
            @Value("${anthropic.synthesis-max-output-tokens}")
            long synthesisMaxOutputTokens,
            @Value("${anthropic.cache.ttl:5m}") String cacheTtl
    ) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.synthesisMaxOutputTokens = synthesisMaxOutputTokens;
        this.cacheControl = buildCacheControl(cacheTtl);
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
                prompt, intensity, intensity.scaleTokens(maxOutputTokens));

        Message response = client.messages().create(params);

        String content = response.content().stream()
                .flatMap(contentBlock -> contentBlock.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        if (content.isBlank()) {
            throw new IllegalStateException("Anthropic boş bir cevap döndürdü.");
        }

        return new AiResult(content, usageOf(response.usage()));
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
        MessageCreateParams params =
                createParams(prompt, intensity, outputTokenLimit);

        AtomicLong inputTokens = new AtomicLong(0);
        AtomicLong outputTokens = new AtomicLong(0);
        AtomicLong cacheReadTokens = new AtomicLong(0);
        AtomicLong cacheWriteTokens = new AtomicLong(0);

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
                event.messageStart().ifPresent(start -> {
                    Usage usage = start.message().usage();
                    inputTokens.set(usage.inputTokens());
                    cacheReadTokens.set(
                            usage.cacheReadInputTokens().orElse(0L));
                    cacheWriteTokens.set(
                            usage.cacheCreationInputTokens().orElse(0L));
                });
                event.messageDelta().ifPresent(delta ->
                        outputTokens.set(delta.usage().outputTokens()));
            });
        }

        return new TokenUsage(
                inputTokens.get(),
                outputTokens.get(),
                cacheReadTokens.get(),
                cacheWriteTokens.get()
        );
    }

    /**
     * İsteği kurar ve stabil prefix'i açık bir cache breakpoint'iyle işaretler.
     *
     * <p>Breakpoint prefix'in sonundadır: sonraki istekler aynı prefix'i
     * okur, değişen kuyruk cache'e yazılmaz. Prefix modelin minimum eşiğinin
     * altındaysa işaretleme sessizce etkisizdir — yazma primi de doğmaz — o
     * yüzden bir eşik koşulu koymuyoruz.
     */
    private MessageCreateParams createParams(
            PromptParts prompt,
            ResponseIntensity intensity,
            long outputTokenLimit
    ) {
        // Yoğunluk yönergesi yalnızca değişken kuyruğa girer; prefix'in
        // başındaki bir değişiklik tüm cache'i geçersiz kılardı.
        String suffix = intensity.applyTo(prompt.volatileSuffix());

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(outputTokenLimit);

        if (!prompt.hasCacheablePrefix()) {
            return builder.addUserMessage(suffix).build();
        }

        return builder
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(prompt.cacheablePrefix())
                                .cacheControl(cacheControl)
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(suffix)
                                .build())))
                .build();
    }

    private static TokenUsage usageOf(Usage usage) {
        return new TokenUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L),
                usage.cacheCreationInputTokens().orElse(0L)
        );
    }

    /**
     * 1 saatlik TTL yazma primini 2×'e çıkarır ve başabaş için 3+ istek
     * gerektirir; karşılaştırma modunda ardışık turlar genelde 5 dakikadan
     * yakın olduğu için varsayılan 5 dakikadır.
     */
    private static CacheControlEphemeral buildCacheControl(String cacheTtl) {
        CacheControlEphemeral.Builder builder = CacheControlEphemeral.builder();

        return TTL_ONE_HOUR.equalsIgnoreCase(cacheTtl.trim())
                ? builder.ttl(CacheControlEphemeral.Ttl.TTL_1H).build()
                : builder.ttl(CacheControlEphemeral.Ttl.TTL_5M).build();
    }
}
