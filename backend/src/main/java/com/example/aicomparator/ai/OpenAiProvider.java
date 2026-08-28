package com.example.aicomparator.ai;

import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.example.aicomparator.dto.AiResult;
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
    public AiResult sendMessage(String message, ResponseIntensity intensity) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(intensity.applyTo(message))
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
                .map(u -> new TokenUsage(u.inputTokens(), u.outputTokens()))
                .orElse(TokenUsage.EMPTY);

        return new AiResult(content, usage);
    }

    @Override
    public TokenUsage streamMessage(
            String userMessage,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(userMessage, intensity, onToken,
                intensity.scaleTokens(maxOutputTokens));
    }

    @Override
    public TokenUsage streamSynthesisMessage(
            String userMessage,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamWithLimit(userMessage, intensity, onToken,
                intensity.scaleTokens(synthesisMaxOutputTokens));
    }

    private TokenUsage streamWithLimit(
            String userMessage,
            ResponseIntensity intensity,
            Consumer<String> onToken,
            long outputTokenLimit
    ) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(intensity.applyTo(userMessage))
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
                        done.response().usage().ifPresent(u ->
                                usage.set(new TokenUsage(
                                        u.inputTokens(), u.outputTokens()))));
            });
        }

        return usage.get();
    }
}
