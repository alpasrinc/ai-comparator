package com.example.aicomparator.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.service.AiComparisonService;
import com.example.aicomparator.ai.AnthropicProvider;
import com.example.aicomparator.ai.GeminiProvider;
import com.example.aicomparator.ai.OpenAiProvider;
import com.example.aicomparator.dto.AiResponse;

import com.example.aicomparator.dto.ChatRequest;
import com.example.aicomparator.dto.RetryProviderRequest;

import com.example.aicomparator.entity.AiProviderType;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OpenAiProvider openAiProvider;
    private final AnthropicProvider anthropicProvider;
    private final GeminiProvider geminiProvider;
    private final AiComparisonService aiComparisonService;

    public ChatController(
        OpenAiProvider openAiProvider,
        AnthropicProvider anthropicProvider,
        GeminiProvider geminiProvider,
        AiComparisonService aiComparisonService
) {
    this.openAiProvider = openAiProvider;
    this.anthropicProvider = anthropicProvider;
    this.geminiProvider = geminiProvider;
    this.aiComparisonService = aiComparisonService;

}
@PostMapping("/compare")
public CompareResponse compare(
        @Valid @RequestBody ChatRequest request
) {
    return aiComparisonService.compare(
            request.conversationId(),
            request.message().trim()
    );
}

    @PostMapping("/retry")
    public AiResponse retryProvider(
            @Valid @RequestBody RetryProviderRequest request
    ) {
        return aiComparisonService.retryProvider(
                request.conversationId(),
                request.userMessageId(),
                request.provider()
        );
    }

    @PostMapping("/openai")
    public AiResponse sendToOpenAi(
            @Valid @RequestBody ChatRequest request
    ) {
        String content = openAiProvider.sendMessage(request.message().trim());

        return AiResponse.success(
                null,
                AiProviderType.OPENAI.name(),
                content
        );
    }

    @PostMapping("/anthropic")
    public AiResponse sendToAnthropic(
            @Valid @RequestBody ChatRequest request
    ) {
        String content = anthropicProvider.sendMessage(request.message().trim());

        return AiResponse.success(
                null,
                AiProviderType.ANTHROPIC.name(),
                content
        );
    }

    @PostMapping("/gemini")
    public AiResponse sendToGemini(
            @Valid @RequestBody ChatRequest request
    ) {
        String content = geminiProvider.sendMessage(request.message().trim());

        return AiResponse.success(
                null,
                AiProviderType.GEMINI.name(),
                content
        );
    }
}
