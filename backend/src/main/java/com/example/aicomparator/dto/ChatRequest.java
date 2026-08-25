package com.example.aicomparator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        Long conversationId,

        @NotBlank(message = "Mesaj boş olamaz.")
        @Size(
                max = 8000,
                message = "Mesaj çok uzun (en fazla 8000 karakter olmalı)."
        )
        String message

) {
}