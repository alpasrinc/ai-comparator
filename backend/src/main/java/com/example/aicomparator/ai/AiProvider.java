package com.example.aicomparator.ai;

import java.util.function.Consumer;

import com.example.aicomparator.dto.AiResult;
import com.example.aicomparator.dto.PromptParts;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.entity.AiProviderType;

public interface AiProvider {

    AiProviderType getProviderType();

    AiResult sendMessage(PromptParts prompt, ResponseIntensity intensity);

    /**
     * Cevabı parça parça üretir; her metin parçası geldiğinde onToken
     * çağrılır. Akış tamamlanana kadar bloklar ve toplam token kullanımını
     * döndürür.
     */
    TokenUsage streamMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    );

    /**
     * Münazara sonunda daha uzun ortak cevap üretmek için sağlayıcının
     * senteze özel çıktı limitini kullanır.
     */
    default TokenUsage streamSynthesisMessage(
            PromptParts prompt,
            ResponseIntensity intensity,
            Consumer<String> onToken
    ) {
        return streamMessage(prompt, intensity, onToken);
    }
}
