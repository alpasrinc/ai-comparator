package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.aicomparator.dto.DebateDetailResponse;
import com.example.aicomparator.dto.DebateRequest;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.repository.DebateMessageRepository;
import com.example.aicomparator.repository.DebateRepository;

@SpringBootTest
@Transactional
class DebateServiceIntegrationTests {

    @Autowired
    private DebateService debateService;

    @Autowired
    private DebateRepository debateRepository;

    @Autowired
    private DebateMessageRepository debateMessageRepository;

    @Test
    void createsDebateInRunningStatusWithParticipants() {
        DebateRequest request = new DebateRequest(
                "Konu A",
                List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                2,
                AiProviderType.OPENAI,
                ResponseIntensity.MEDIUM
        );

        Long debateId = debateService.createDebate(request);
        DebateDetailResponse detail = debateService.getDebate(debateId);

        assertThat(detail.status()).isEqualTo("RUNNING");
        assertThat(detail.participants())
                .containsExactlyInAnyOrder("OPENAI", "GEMINI");
        assertThat(detail.messages()).isEmpty();
    }

    @Test
    void savesParticipantAndSynthesisMessagesAndCompletes() {
        Long debateId = debateService.createDebate(new DebateRequest(
                "Konu B",
                List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                1,
                AiProviderType.OPENAI,
                ResponseIntensity.MEDIUM
        ));

        debateService.saveParticipantMessage(
                debateId, 1, AiProviderType.OPENAI, "openai cevap");
        debateService.saveSynthesisMessage(
                debateId, AiProviderType.OPENAI, "ortak cevap");

        DebateDetailResponse detail = debateService.getDebate(debateId);

        assertThat(detail.status()).isEqualTo("COMPLETED");
        assertThat(detail.finalAnswer()).isEqualTo("ortak cevap");
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).role()).isEqualTo("PARTICIPANT");
        assertThat(detail.messages().get(1).role()).isEqualTo("SYNTHESIS");
    }

    @Test
    void listsDebatesNewestFirst() {
        debateService.createDebate(new DebateRequest(
                "Eski", List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                1, AiProviderType.OPENAI, ResponseIntensity.MEDIUM));
        Long newer = debateService.createDebate(new DebateRequest(
                "Yeni", List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                1, AiProviderType.OPENAI, ResponseIntensity.MEDIUM));

        assertThat(debateService.listDebates())
                .isNotEmpty()
                .first()
                .satisfies(d -> assertThat(d.id()).isEqualTo(newer));
    }

    @Test
    void deletesDebateWithParticipantsAndMessages() {
        Long debateId = debateService.createDebate(new DebateRequest(
                "Silinecek münazara",
                List.of(AiProviderType.OPENAI, AiProviderType.GEMINI),
                1,
                AiProviderType.OPENAI,
                ResponseIntensity.MEDIUM
        ));

        debateService.saveParticipantMessage(
                debateId, 1, AiProviderType.OPENAI, "Katılımcı cevabı");
        debateService.saveSynthesisMessage(
                debateId, AiProviderType.OPENAI, "Ortak cevap");

        debateService.deleteDebate(debateId);

        assertThat(debateRepository.findById(debateId)).isEmpty();
        assertThat(debateMessageRepository
                .findByDebateIdOrderByIdAsc(debateId))
                .isEmpty();
    }
}
