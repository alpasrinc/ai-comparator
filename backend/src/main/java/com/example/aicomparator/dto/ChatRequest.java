package com.example.aicomparator.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(

        Long conversationId,

        @NotBlank(message = "Mesaj boş olamaz.")
        String message

) {
}