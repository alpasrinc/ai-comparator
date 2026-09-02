package com.example.aicomparator.ai;

import java.util.List;

/**
 * Metni vektöre çeviren sağlayıcı.
 *
 * <p>Bilinçli olarak {@code AiProvider}'dan ayrı: Anthropic'in embedding
 * modeli yok, dolayısıyla "üç sağlayıcı eşit" simetrisi burada geçerli değil.
 *
 * <p><b>Kritik kısıt:</b> indeksleme ve sorgu aynı modeli kullanmak zorunda.
 * Model değişirse eski vektörler yeni sorgu vektörüyle kıyaslanamaz ve arama
 * sessizce yanlış sonuç verir; bu yüzden {@link #modelName()} her parçayla
 * birlikte saklanır.
 */
public interface EmbeddingProvider {

    String modelName();

    List<float[]> embedBatch(List<String> texts);

    default float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }
}
