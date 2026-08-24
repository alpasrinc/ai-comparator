package com.example.aicomparator.dto;

import java.util.List;

public record CompareResponse(
        List<AiResponse> responses
) {
}