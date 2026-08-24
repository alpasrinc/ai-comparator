package com.example.aicomparator.ai;

import com.example.aicomparator.entity.AiProviderType;

public interface AiProvider {

    AiProviderType getProviderType();

    String sendMessage(String userMessage);
}