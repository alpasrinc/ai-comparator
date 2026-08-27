package com.example.aicomparator.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.aicomparator.entity.AiProviderType;

public record DebateRequest(
        @NotBlank(message = "Konu boş olamaz.")
        String topic,

        @NotNull
        @Size(min = 2, message = "En az iki katılımcı seçilmeli.")
        List<AiProviderType> participants,

        @Min(value = 1, message = "Tur sayısı en az 1 olmalı.")
        @Max(value = 5, message = "Tur sayısı en fazla 5 olmalı.")
        int rounds,

        @NotNull(message = "Sentezci seçilmeli.")
        AiProviderType synthesizer
) {
}
