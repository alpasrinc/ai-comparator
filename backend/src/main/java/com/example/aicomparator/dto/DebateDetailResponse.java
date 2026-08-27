package com.example.aicomparator.dto;

import java.util.List;

public record DebateDetailResponse(
        Long id,
        String topic,
        int rounds,
        List<String> participants,
        String synthesizer,
        String status,
        String finalAnswer,
        List<DebateMessageResponse> messages
) {
}
