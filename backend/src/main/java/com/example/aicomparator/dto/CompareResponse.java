package com.example.aicomparator.dto;

import java.util.List;

public record CompareResponse(
        Long conversationId,
        Long userMessageId,
        List<AiResponse> responses
) {
}