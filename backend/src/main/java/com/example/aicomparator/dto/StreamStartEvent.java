package com.example.aicomparator.dto;

import java.util.List;

public record StreamStartEvent(
        Long conversationId,
        Long userMessageId,
        List<RetrievedChunk> sources,
        boolean sourcesUnavailable
) {
}
