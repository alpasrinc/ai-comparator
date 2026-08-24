package com.example.aicomparator.ai;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.aicomparator.entity.AiProviderType;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

@Service
public class AnthropicProvider implements AiProvider  {

    private final AnthropicClient client;
    private final String model;
    private final long maxOutputTokens;

    public AnthropicProvider(
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.max-output-tokens}") long maxOutputTokens
    ) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
public AiProviderType getProviderType() {
    return AiProviderType.ANTHROPIC;
}

    @Override
    public String sendMessage(String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxOutputTokens)
                .addUserMessage(userMessage)
                .build();

        Message response = client.messages().create(params);

        String content = response.content().stream()
                .flatMap(contentBlock -> contentBlock.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));

        if (content.isBlank()) {
            throw new IllegalStateException("Anthropic boş bir cevap döndürdü.");
        }

        return content;
    }
}