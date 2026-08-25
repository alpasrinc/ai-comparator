package com.example.aicomparator.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aicomparator.dto.ActiveMessageResponse;
import com.example.aicomparator.dto.SelectMessageRequest;
import com.example.aicomparator.service.ConversationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    @PostMapping("/{conversationId}/active-message")
    public ActiveMessageResponse selectActiveMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SelectMessageRequest request
    ) {
        return conversationService.selectActiveMessage(
                conversationId,
                request.messageId()
        );
    }
}