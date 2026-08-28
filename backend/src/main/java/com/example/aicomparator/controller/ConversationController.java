package com.example.aicomparator.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.example.aicomparator.dto.ConversationDetailResponse;
import com.example.aicomparator.dto.ConversationSummaryResponse;
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

    @GetMapping
public List<ConversationSummaryResponse> getConversations() {
    return conversationService.getConversations();
}

@GetMapping("/{conversationId}")
public ConversationDetailResponse getConversation(
        @PathVariable Long conversationId
) {
    return conversationService.getConversation(conversationId);
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

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable Long conversationId) {
        conversationService.deleteConversation(conversationId);
    }
}
