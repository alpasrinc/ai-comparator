package com.example.aicomparator.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.aicomparator.entity.AiProviderType;

@Component
public class DebatePromptBuilder {

    public String buildFirstRoundPrompt(
            String topic,
            AiProviderType self
    ) {
        return """
                Bir uzman panelinde münazaraya katılıyorsun. Kimliğin: %s.
                Aşağıdaki konu hakkında net, gerekçeli ve öz bir ilk görüş sun.

                KONU:
                %s
                """.formatted(self.name(), topic);
    }

    public String buildCritiqueRoundPrompt(
            String topic,
            AiProviderType self,
            List<Map<AiProviderType, String>> transcript
    ) {
        Map<AiProviderType, String> previousRound =
                transcript.get(transcript.size() - 1);

        return """
                Aynı münazaraya devam ediyorsun. Kimliğin: %s.
                Konu: %s

                Bir önceki turda katılımcıların verdiği cevaplar:
                %s

                Diğer katılımcıların argümanlarını eleştirel biçimde değerlendir,
                haklı buldukların varsa görüşünü revize et, katılmadıklarını
                gerekçelendir. Güncellenmiş görüşünü öz biçimde yaz.
                """.formatted(self.name(), topic, formatRound(previousRound));
    }

    public String buildSynthesisPrompt(
            String topic,
            List<Map<AiProviderType, String>> transcript
    ) {
        StringBuilder rounds = new StringBuilder();
        for (int i = 0; i < transcript.size(); i++) {
            rounds.append("=== Tur ").append(i + 1).append(" ===\n");
            rounds.append(formatRound(transcript.get(i))).append("\n");
        }

        return """
                Sen tarafsız bir moderatörsün. Aşağıdaki münazarayı okudun.
                Hiçbir katılımcıyı kayırma; argümanları değerine göre tart.
                Konu hakkında katılımcıların ortaklaştığı ve ayrıştığı noktaları
                gözeterek TEK, net ve gerekçeli bir ortak cevap yaz.

                KONU:
                %s

                MÜNAZARA:
                %s
                """.formatted(topic, rounds.toString());
    }

    private String formatRound(Map<AiProviderType, String> round) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<AiProviderType, String> entry : round.entrySet()) {
            String answer = entry.getValue() == null || entry.getValue().isBlank()
                    ? "(cevap alınamadı)"
                    : entry.getValue();
            builder.append("- ")
                    .append(entry.getKey().name())
                    .append(": ")
                    .append(answer)
                    .append("\n");
        }
        return builder.toString();
    }
}
