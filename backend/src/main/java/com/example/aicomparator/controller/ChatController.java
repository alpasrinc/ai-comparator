package com.example.aicomparator.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aicomparator.ai.OpenAiProvider;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.OpenAiChatRequest;
import com.example.aicomparator.entity.AiProviderType;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OpenAiProvider openAiProvider;

    public ChatController(OpenAiProvider openAiProvider) {
        this.openAiProvider = openAiProvider;
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
}