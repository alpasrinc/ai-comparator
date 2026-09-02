package com.example.aicomparator.dto;

import java.util.List;

public record CompareResponse(
        Long conversationId,
        Long userMessageId,
        List<AiResponse> responses,
        List<RetrievedChunk> sources,
        boolean sourcesUnavailable
) {

    public CompareResponse(
            Long conversationId,
            Long userMessageId,
            List<AiResponse> responses
    ) {
        this(conversationId, userMessageId, responses, List.of(), false);
    }
}
