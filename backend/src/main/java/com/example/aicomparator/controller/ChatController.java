package com.example.aicomparator.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.service.AiComparisonService;
import com.example.aicomparator.dto.AiResponse;

import com.example.aicomparator.dto.ChatRequest;
import com.example.aicomparator.dto.RetryProviderRequest;

import com.example.aicomparator.entity.AiProviderType;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiComparisonService aiComparisonService;
    private final long sseTimeoutMillis;

    public ChatController(
            AiComparisonService aiComparisonService,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds
    ) {
        this.aiComparisonService = aiComparisonService;
        this.sseTimeoutMillis = (requestTimeoutSeconds + 15) * 1000L;
    }

    @PostMapping("/compare")
    public CompareResponse compare(
            @Valid @RequestBody ChatRequest request
    ) {
        return aiComparisonService.compare(
                request.conversationId(),
                request.message().trim(),
                request.providers(),
                request.intensity()
        );
    }

    @PostMapping(
            value = "/compare/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter compareStream(
            @Valid @RequestBody ChatRequest request
    ) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);

        aiComparisonService.streamCompare(
                request.conversationId(),
                request.message().trim(),
                request.providers(),
                request.intensity(),
                emitter
        );

        return emitter;
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
        return aiComparisonService.sendSingle(
                AiProviderType.OPENAI,
                request.message().trim()
        );
    }

    @PostMapping("/anthropic")
    public AiResponse sendToAnthropic(
            @Valid @RequestBody ChatRequest request
    ) {
        return aiComparisonService.sendSingle(
                AiProviderType.ANTHROPIC,
                request.message().trim()
        );
    }

    @PostMapping("/gemini")
    public AiResponse sendToGemini(
            @Valid @RequestBody ChatRequest request
    ) {
        return aiComparisonService.sendSingle(
                AiProviderType.GEMINI,
                request.message().trim()
        );
    }
}
