package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aicomparator.entity.AiProviderType;

class DebatePromptBuilderTests {

    private final DebatePromptBuilder builder = new DebatePromptBuilder();

    @Test
    void firstRoundPromptIncludesTopic() {
        String prompt = builder.buildFirstRoundPrompt(
                "Uzayda zaman nasıl işler?",
                AiProviderType.OPENAI
        );

        assertThat(prompt).contains("Uzayda zaman nasıl işler?");
    }

    @Test
    void critiqueRoundPromptIncludesPreviousRoundAnswers() {
        List<Map<AiProviderType, String>> transcript = List.of(
                Map.of(
                        AiProviderType.OPENAI, "OpenAI ilk cevap",
                        AiProviderType.GEMINI, "Gemini ilk cevap"
                )
        );

        String prompt = builder.buildCritiqueRoundPrompt(
                "Konu X",
                AiProviderType.OPENAI,
                transcript
        );

        assertThat(prompt)
                .contains("OpenAI ilk cevap")
                .contains("Gemini ilk cevap")
                .contains("Konu X");
    }

    @Test
    void synthesisPromptIsNeutralAndContainsFullTranscript() {
        List<Map<AiProviderType, String>> transcript = List.of(
                Map.of(AiProviderType.OPENAI, "tur1-openai"),
                Map.of(AiProviderType.OPENAI, "tur2-openai")
        );

        String prompt = builder.buildSynthesisPrompt("Konu Y", transcript);

        assertThat(prompt)
                .contains("tarafsız")
                .contains("tur1-openai")
                .contains("tur2-openai")
                .contains("Konu Y");
    }

    @Test
    void transcriptMarksMissingAnswerForFailedParticipant() {
        List<Map<AiProviderType, String>> transcript = List.of(
                Map.of(AiProviderType.OPENAI, "")
        );

        String prompt = builder.buildCritiqueRoundPrompt(
                "Konu Z",
                AiProviderType.OPENAI,
                transcript
        );

        assertThat(prompt).contains("cevap alınamadı");
    }
}
