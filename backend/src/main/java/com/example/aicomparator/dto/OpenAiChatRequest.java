package com.example.aicomparator.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenAiChatRequest(

        @NotBlank(message = "Mesaj boş olamaz.")
        String message

) {
}