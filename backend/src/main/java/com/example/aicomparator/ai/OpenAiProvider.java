package com.example.aicomparator.ai;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

@Service
public class OpenAiProvider {

    private final OpenAIClient client;
    private final String model;

    public OpenAiProvider(@Value("${openai.model}") String model) {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = model;
    }

    public String sendMessage(String message) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(message)
                .build();

        Response response = client.responses().create(params);

        String content = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(outputMessage -> outputMessage.content().stream())
                .flatMap(outputContent -> outputContent.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining("\n"));

        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI boş bir cevap döndürdü.");
        }

        return content;
    }
}