package com.example.aicomparator.ai;

import java.util.function.Consumer;

import com.example.aicomparator.entity.AiProviderType;

public interface AiProvider {

    AiProviderType getProviderType();

    String sendMessage(String userMessage);

    /**
     * Cevabı parça parça üretir; her metin parçası geldiğinde onToken
     * çağrılır. Akış tamamlanana kadar bloklar.
     */
    void streamMessage(String userMessage, Consumer<String> onToken);

    /**
     * Münazara sonunda daha uzun ortak cevap üretmek için sağlayıcının
     * senteze özel çıktı limitini kullanır.
     */
    default void streamSynthesisMessage(
            String userMessage,
            Consumer<String> onToken
    ) {
        streamMessage(userMessage, onToken);
    }
}
