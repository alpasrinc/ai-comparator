package com.example.aicomparator.dto;

import jakarta.validation.constraints.NotNull;

public record SelectMessageRequest(

        @NotNull(message = "Seçilecek mesaj ID'si zorunludur.")
        Long messageId

) {
}