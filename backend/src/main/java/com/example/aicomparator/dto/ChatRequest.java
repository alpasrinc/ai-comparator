package com.example.aicomparator.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.aicomparator.entity.AiProviderType;

public record ChatRequest(

        Long conversationId,

        @NotBlank(message = "Mesaj boş olamaz.")
        @Size(
                max = 8000,
                message = "Mesaj çok uzun (en fazla 8000 karakter olmalı)."
        )
        String message,

        @Size(
                min = 1,
                max = 3,
                message = "En az bir, en fazla üç AI seçilmelidir."
        )
        List<AiProviderType> providers,

        ResponseIntensity intensity

) {
}
