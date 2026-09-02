package com.example.aicomparator.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.aicomparator.service.VectorMath;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;

@Service
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final OpenAIClient client;
    private final String model;

    public OpenAiEmbeddingProvider(
            @Value("${openai.embedding-model}") String model
    ) {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = model;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.of(model))
                .inputOfArrayOfStrings(texts)
                .build();

        CreateEmbeddingResponse response = client.embeddings().create(params);

        // Sıra garanti değil; index alanına göre sıralıyoruz. Yanlış sıra
        // parçaları birbirinin vektörüyle eşleştirir ve bunu hiçbir hata
        // mesajı göstermez.
        List<Embedding> ordered = new ArrayList<>(response.data());
        ordered.sort(Comparator.comparingLong(Embedding::index));

        List<float[]> vectors = new ArrayList<>(ordered.size());

        for (Embedding embedding : ordered) {
            List<Float> values = embedding.embedding();
            float[] vector = new float[values.size()];

            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }

            // Yazarken normalize: sorgu anında kosinüs nokta çarpımına iner.
            vectors.add(VectorMath.normalize(vector));
        }

        if (vectors.size() != texts.size()) {
            throw new IllegalStateException(
                    "Embedding sayısı metin sayısıyla uyuşmuyor: "
                            + vectors.size() + " / " + texts.size());
        }

        return List.copyOf(vectors);
    }
}
