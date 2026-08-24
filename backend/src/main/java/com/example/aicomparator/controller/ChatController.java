package com.example.aicomparator.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aicomparator.ai.AnthropicProvider;
import com.example.aicomparator.ai.GeminiProvider;
import com.example.aicomparator.ai.OpenAiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.AnthropicChatRequest;
import com.example.aicomparator.dto.GeminiChatRequest;
import com.example.aicomparator.dto.OpenAiChatRequest;
import com.example.aicomparator.entity.AiProviderType;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OpenAiProvider openAiProvider;
    private final AnthropicProvider anthropicProvider;
    private final GeminiProvider geminiProvider;

    public ChatController(
            OpenAiProvider openAiProvider,
            AnthropicProvider anthropicProvider,
            GeminiProvider geminiProvider
    ) {
        this.openAiProvider = openAiProvider;
        this.anthropicProvider = anthropicProvider;
        this.geminiProvider = geminiProvider;
    }

    @PostMapping("/openai")
    public AiResponse sendToOpenAi(
            @Valid @RequestBody OpenAiChatRequest request
    ) {
        String content = openAiProvider.sendMessage(request.message().trim());

        return new AiResponse(
                AiProviderType.OPENAI.name(),
                content
        );
    }

    @PostMapping("/anthropic")
    public AiResponse sendToAnthropic(
            @Valid @RequestBody AnthropicChatRequest request
    ) {
        String content = anthropicProvider.sendMessage(request.message().trim());

        return new AiResponse(
                AiProviderType.ANTHROPIC.name(),
                content
        );
    }

    @PostMapping("/gemini")
    public AiResponse sendToGemini(
            @Valid @RequestBody GeminiChatRequest request
    ) {
        String content = geminiProvider.sendMessage(request.message().trim());

        return new AiResponse(
                AiProviderType.GEMINI.name(),
                content
        );
    }
}