package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.dto.PromptParts;
import com.example.aicomparator.dto.ResponseIntensity;
import com.example.aicomparator.dto.RetrievedChunk;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.Document;
import com.example.aicomparator.entity.DocumentChunk;
import com.example.aicomparator.entity.Message;
import com.example.aicomparator.entity.MessageRole;
import com.example.aicomparator.repository.ConversationRepository;
import com.example.aicomparator.repository.DocumentChunkRepository;
import com.example.aicomparator.repository.DocumentRepository;
import com.example.aicomparator.repository.MessageRepository;

@SpringBootTest
@Transactional
class ConversationServiceIntegrationTests {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Test
    void shouldSaveComparisonWithUserAndAssistantMessages() {
        List<AiResponse> aiResponses = List.of(
                new AiResponse(null, "OPENAI", "OpenAI cevabı"),
                new AiResponse(null, "ANTHROPIC", "Claude cevabı"),
                new AiResponse(null, "GEMINI", "Gemini cevabı")
        );

        CompareResponse result = conversationService.saveComparison(
                "Spring Boot nedir?",
                aiResponses
        );

        assertThat(result.conversationId()).isNotNull();
        assertThat(result.userMessageId()).isNotNull();
        assertThat(result.responses())
                .hasSize(3)
                .allSatisfy(response ->
                        assertThat(response.messageId()).isNotNull()
                );

        assertThat(
                conversationRepository.findById(result.conversationId())
        )
                .isPresent()
                .get()
                .extracting(conversation -> conversation.getTitle())
                .isEqualTo("Spring Boot nedir?");

        List<Message> savedMessages =
                messageRepository.findByConversation_IdOrderByCreatedAtAsc(
                        result.conversationId()
                );

        assertThat(savedMessages).hasSize(4);

        assertThat(savedMessages)
                .filteredOn(message ->
                        message.getRole() == MessageRole.USER
                )
                .singleElement()
                .satisfies(userMessage -> {
                    assertThat(userMessage.getId())
                            .isEqualTo(result.userMessageId());
                    assertThat(userMessage.getProvider()).isNull();
                    assertThat(userMessage.getParentMessage()).isNull();
                });

        assertThat(savedMessages)
                .filteredOn(message ->
                        message.getRole() == MessageRole.ASSISTANT
                )
                .hasSize(3)
                .allSatisfy(assistantMessage ->
                        assertThat(
                                assistantMessage.getParentMessage().getId()
                        ).isEqualTo(result.userMessageId())
                )
                .extracting(Message::getProvider)
                .containsExactlyInAnyOrder(
                        AiProviderType.OPENAI,
                        AiProviderType.ANTHROPIC,
                        AiProviderType.GEMINI
                );
    }
    @Test
void shouldContinueFromSelectedAssistantMessage() {
    CompareResponse firstComparison = conversationService.saveComparison(
            "Java nedir?",
            List.of(
                    new AiResponse(null, "OPENAI", "OpenAI alternatif cevabı"),
                    new AiResponse(null, "ANTHROPIC", "Seçilen Claude cevabı"),
                    new AiResponse(null, "GEMINI", "Gemini alternatif cevabı")
            )
    );

    AiResponse selectedResponse = firstComparison.responses().stream()
            .filter(response ->
                    response.provider().equals("ANTHROPIC")
            )
            .findFirst()
            .orElseThrow();

    var selection = conversationService.selectActiveMessage(
            firstComparison.conversationId(),
            selectedResponse.messageId()
    );

    assertThat(selection.activeMessageId())
            .isEqualTo(selectedResponse.messageId());
    assertThat(selection.provider())
            .isEqualTo("ANTHROPIC");

    String contextPrompt =
            conversationService.buildActiveContextPrompt(
                    firstComparison.conversationId(),
                    "Bir örnek verir misin?",
                    AiProviderType.OPENAI
            ).joined();

    assertThat(contextPrompt)
            .contains(
                    "OpenAI (ChatGPT)",
                    "USER: Java nedir?",
                    "ANTHROPIC cevabı: Seçilen Claude cevabı",
                    "USER: Bir örnek verir misin?"
            )
            .doesNotContain(
                    "OpenAI alternatif cevabı",
                    "Gemini alternatif cevabı",
                    "ASSISTANT: Seçilen Claude cevabı"
            );

    CompareResponse continuation =
            conversationService.saveContinuation(
                    firstComparison.conversationId(),
                    "Bir örnek verir misin?",
                    List.of(
                            new AiResponse(null, "OPENAI", "Yeni OpenAI cevabı"),
                            new AiResponse(null, "ANTHROPIC", "Yeni Claude cevabı"),
                            new AiResponse(null, "GEMINI", "Yeni Gemini cevabı")
                    )
            );

    assertThat(continuation.conversationId())
            .isEqualTo(firstComparison.conversationId());

    Message continuedUserMessage = messageRepository
            .findById(continuation.userMessageId())
            .orElseThrow();

    assertThat(continuedUserMessage.getParentMessage().getId())
            .isEqualTo(selectedResponse.messageId());

    assertThat(
            messageRepository.findByParentMessage_IdOrderByCreatedAtAsc(
                    continuation.userMessageId()
            )
    )
            .hasSize(3)
            .extracting(Message::getProvider)
            .containsExactlyInAnyOrder(
                    AiProviderType.OPENAI,
                    AiProviderType.ANTHROPIC,
                    AiProviderType.GEMINI
            );

    assertThat(
            conversationRepository
                    .findById(firstComparison.conversationId())
                    .orElseThrow()
                    .getActiveMessage()
                    .getId()
    ).isEqualTo(selectedResponse.messageId());
}
@Test
void shouldListAndLoadConversationHistory() {
    CompareResponse savedComparison =
            conversationService.saveComparison(
                    "Spring Data JPA nedir?",
                    List.of(
                            new AiResponse(
                                    null,
                                    "OPENAI",
                                    "OpenAI geçmiş cevabı"
                            ),
                            new AiResponse(
                                    null,
                                    "ANTHROPIC",
                                    "Claude geçmiş cevabı"
                            ),
                            new AiResponse(
                                    null,
                                    "GEMINI",
                                    "Gemini geçmiş cevabı"
                            )
                    )
            );

    assertThat(conversationService.getConversations())
            .anySatisfy(conversation -> {
                assertThat(conversation.id())
                        .isEqualTo(savedComparison.conversationId());
                assertThat(conversation.title())
                        .isEqualTo("Spring Data JPA nedir?");
                assertThat(conversation.activeMessageId())
                        .isNull();
            });

    var conversationDetail = conversationService.getConversation(
            savedComparison.conversationId()
    );

    assertThat(conversationDetail.id())
            .isEqualTo(savedComparison.conversationId());
    assertThat(conversationDetail.title())
            .isEqualTo("Spring Data JPA nedir?");
    assertThat(conversationDetail.activeMessageId()).isNull();

    assertThat(conversationDetail.messages())
            .hasSize(4);

    assertThat(conversationDetail.messages())
            .filteredOn(message -> message.role().equals("USER"))
            .singleElement()
            .satisfies(message -> {
                assertThat(message.id())
                        .isEqualTo(savedComparison.userMessageId());
                assertThat(message.parentMessageId()).isNull();
                assertThat(message.provider()).isNull();
                assertThat(message.content())
                        .isEqualTo("Spring Data JPA nedir?");
            });

    assertThat(conversationDetail.messages())
            .filteredOn(message -> message.role().equals("ASSISTANT"))
            .hasSize(3)
            .extracting(message -> message.provider())
            .containsExactlyInAnyOrder(
                    "OPENAI",
                    "ANTHROPIC",
                    "GEMINI"
            );
}

@Test
void shouldLabelHistoricalResponsesWithProviderNameInRetryPrompt() {
    CompareResponse firstComparison = conversationService.saveComparison(
            "Java nedir?",
            List.of(
                    new AiResponse(null, "OPENAI", "OpenAI cevabı"),
                    new AiResponse(null, "ANTHROPIC", "Claude cevabı"),
                    new AiResponse(null, "GEMINI", "Gemini cevabı")
            )
    );

    AiResponse selectedResponse = firstComparison.responses().stream()
            .filter(response -> response.provider().equals("ANTHROPIC"))
            .findFirst()
            .orElseThrow();

    conversationService.selectActiveMessage(
            firstComparison.conversationId(),
            selectedResponse.messageId()
    );

    CompareResponse continuation = conversationService.saveContinuation(
            firstComparison.conversationId(),
            "Bir örnek verir misin?",
            List.of(
                    new AiResponse(null, "OPENAI", "Yeni OpenAI cevabı"),
                    new AiResponse(null, "GEMINI", "Yeni Gemini cevabı")
            )
    );

    String retryPrompt = conversationService.buildPromptForUserMessage(
            continuation.conversationId(),
            continuation.userMessageId(),
            AiProviderType.GEMINI
    ).joined();

    assertThat(retryPrompt)
            .contains(
                    "Google (Gemini)",
                    "USER: Java nedir?",
                    "ANTHROPIC cevabı: Claude cevabı",
                    "USER: Bir örnek verir misin?"
            )
            .doesNotContain("ASSISTANT: Claude cevabı");
}

@Test
void shouldSaveOnlySuccessfulResponsesAndAttachRetriedResponse() {
    CompareResponse comparison = conversationService.saveComparison(
            "Hata yönetimi testi",
            List.of(
                    AiResponse.success(null, "OPENAI", "OpenAI cevabı"),
                    AiResponse.failure(
                            "ANTHROPIC",
                            "Claude yanıtı alınamadı."
                    ),
                    AiResponse.success(null, "GEMINI", "Gemini cevabı")
            )
    );

    assertThat(comparison.responses()).hasSize(3);
    assertThat(comparison.responses())
            .filteredOn(response -> response.error() != null)
            .singleElement()
            .satisfies(response -> {
                assertThat(response.provider()).isEqualTo("ANTHROPIC");
                assertThat(response.messageId()).isNull();
            });

    assertThat(
            messageRepository.findByParentMessage_IdOrderByCreatedAtAsc(
                    comparison.userMessageId()
            )
    ).hasSize(2);

    AiResponse retriedResponse = conversationService.saveRetriedResponse(
            comparison.conversationId(),
            comparison.userMessageId(),
            AiResponse.success(null, "ANTHROPIC", "Yeni Claude cevabı")
    );

    assertThat(retriedResponse.messageId()).isNotNull();
    assertThat(retriedResponse.error()).isNull();

    assertThat(
            messageRepository.findByParentMessage_IdOrderByCreatedAtAsc(
                    comparison.userMessageId()
            )
    )
            .hasSize(3)
            .extracting(Message::getProvider)
            .containsExactlyInAnyOrder(
                    AiProviderType.OPENAI,
                    AiProviderType.ANTHROPIC,
                    AiProviderType.GEMINI
            );
}

@Test
void shouldDeleteConversationWithAllMessages() {
    CompareResponse comparison = conversationService.saveComparison(
            "Silinecek konuşma",
            List.of(
                    new AiResponse(null, "OPENAI", "OpenAI cevabı"),
                    new AiResponse(null, "ANTHROPIC", "Claude cevabı"),
                    new AiResponse(null, "GEMINI", "Gemini cevabı")
            )
    );

    Long selectedMessageId = comparison.responses().get(0).messageId();
    conversationService.selectActiveMessage(
            comparison.conversationId(),
            selectedMessageId
    );

    conversationService.deleteConversation(comparison.conversationId());

    assertThat(conversationRepository.findById(comparison.conversationId()))
            .isEmpty();
    assertThat(messageRepository
            .findByConversation_IdOrderByCreatedAtAsc(
                    comparison.conversationId()
            ))
            .isEmpty();
}

    @Test
    void cacheablePrefixGrowsAsAStrictPrefixAcrossTurns() {
        CompareResponse firstTurn = conversationService.saveComparison(
                "Java nedir?",
                List.of(new AiResponse(null, "ANTHROPIC", "Java bir dildir."))
        );
        conversationService.selectActiveMessage(
                firstTurn.conversationId(),
                firstTurn.responses().get(0).messageId()
        );

        PromptParts turnTwo = conversationService.buildActiveContextPrompt(
                firstTurn.conversationId(),
                "Örnek verir misin?",
                AiProviderType.ANTHROPIC
        );

        CompareResponse secondTurn = conversationService.saveContinuation(
                firstTurn.conversationId(),
                "Örnek verir misin?",
                List.of(new AiResponse(null, "ANTHROPIC", "Şöyle: ..."))
        );
        conversationService.selectActiveMessage(
                firstTurn.conversationId(),
                secondTurn.responses().get(0).messageId()
        );

        PromptParts turnThree = conversationService.buildActiveContextPrompt(
                firstTurn.conversationId(),
                "Peki ya performans?",
                AiProviderType.ANTHROPIC
        );

        // Cache'in çalışmasının tek şartı: önceki prefix, sonrakinin
        // byte-byte öneki olmalı.
        assertThat(turnThree.cacheablePrefix())
                .startsWith(turnTwo.cacheablePrefix())
                .isNotEqualTo(turnTwo.cacheablePrefix());
    }

    @Test
    void cacheablePrefixDoesNotDependOnIntensity() {
        CompareResponse turn = conversationService.saveComparison(
                "Java nedir?",
                List.of(new AiResponse(null, "ANTHROPIC", "Java bir dildir."))
        );
        conversationService.selectActiveMessage(
                turn.conversationId(),
                turn.responses().get(0).messageId()
        );

        PromptParts parts = conversationService.buildActiveContextPrompt(
                turn.conversationId(),
                "Örnek?",
                AiProviderType.ANTHROPIC
        );

        // Yoğunluk yönergesi prefix'te olmamalı; onu ekleyen taraf
        // volatileSuffix üzerinde çalışır.
        assertThat(parts.cacheablePrefix())
                .doesNotContain("Kısa ve öz")
                .doesNotContain("Kapsamlı ve detaylı");
        assertThat(parts.volatileSuffix()).contains("Örnek?");
        assertThat(ResponseIntensity.LOW.applyTo(parts.volatileSuffix()))
                .startsWith("Kısa ve öz");
    }

    @Test
    void retryPromptSharesTheCacheablePrefixOfTheOriginalTurn() {
        CompareResponse firstTurn = conversationService.saveComparison(
                "Java nedir?",
                List.of(new AiResponse(null, "ANTHROPIC", "Java bir dildir."))
        );
        conversationService.selectActiveMessage(
                firstTurn.conversationId(),
                firstTurn.responses().get(0).messageId()
        );

        PromptParts continuation = conversationService.buildActiveContextPrompt(
                firstTurn.conversationId(),
                "Örnek verir misin?",
                AiProviderType.ANTHROPIC
        );

        CompareResponse secondTurn = conversationService.saveContinuation(
                firstTurn.conversationId(),
                "Örnek verir misin?",
                List.of(new AiResponse(null, "ANTHROPIC", "Şöyle: ..."))
        );

        PromptParts retry = conversationService.buildPromptForUserMessage(
                firstTurn.conversationId(),
                secondTurn.userMessageId(),
                AiProviderType.ANTHROPIC
        );

        // "Tekrar dene" aynı dalı yeniden gönderir; aynı prefix'i okumalı ki
        // ilk denemenin yazdığı cache'ten yararlanabilsin.
        assertThat(retry.cacheablePrefix())
                .isEqualTo(continuation.cacheablePrefix());
    }

    @Test
    void retrievedSourcesNeverEnterTheCacheablePrefix() {
        CompareResponse turn = conversationService.saveComparison(
                "Java nedir?",
                List.of(new AiResponse(null, "ANTHROPIC", "Java bir dildir."))
        );
        conversationService.selectActiveMessage(
                turn.conversationId(),
                turn.responses().get(0).messageId()
        );

        List<RetrievedChunk> sources = List.of(new RetrievedChunk(
                1L, 1L, "belge.pdf", 3, "BELGE PARCASI", 0.9));

        PromptParts withSources = conversationService.buildActiveContextPrompt(
                turn.conversationId(), "soru", AiProviderType.ANTHROPIC, sources);
        PromptParts withoutSources = conversationService.buildActiveContextPrompt(
                turn.conversationId(), "soru", AiProviderType.ANTHROPIC, List.of());

        assertThat(withSources.cacheablePrefix())
                .isEqualTo(withoutSources.cacheablePrefix())
                .doesNotContain("BELGE PARCASI");
        assertThat(withSources.volatileSuffix()).contains("BELGE PARCASI");
        assertThat(withSources.volatileSuffix()).contains("belge.pdf");
    }

    @Test
    void theSourcesBlockIsOmittedWhenThereAreNoSources() {
        CompareResponse turn = conversationService.saveComparison(
                "Java nedir?",
                List.of(new AiResponse(null, "ANTHROPIC", "Java bir dildir."))
        );
        conversationService.selectActiveMessage(
                turn.conversationId(),
                turn.responses().get(0).messageId()
        );

        PromptParts parts = conversationService.buildActiveContextPrompt(
                turn.conversationId(), "soru", AiProviderType.ANTHROPIC, List.of());

        assertThat(parts.volatileSuffix()).isEqualTo("USER: soru\n\nASSISTANT:");
    }

    @Test
    void reopeningAConversationRestoresTheSourcesOfEachUserTurn() {
        CompareResponse turn = conversationService.saveComparison(
                "Belgede ne yazıyor?",
                List.of(new AiResponse(null, "ANTHROPIC", "Şu yazıyor."))
        );

        Document document = documentRepository.save(new Document(
                conversationRepository.findById(turn.conversationId())
                        .orElseThrow(),
                "belge.pdf",
                "application/pdf",
                1024L,
                1
        ));
        DocumentChunk chunk = documentChunkRepository.save(new DocumentChunk(
                document, 3, "BELGE PARCASI", new byte[4],
                "text-embedding-3-small"));

        conversationService.saveSources(
                turn.userMessageId(),
                List.of(new RetrievedChunk(
                        chunk.getId(),
                        document.getId(),
                        "belge.pdf",
                        3,
                        "BELGE PARCASI",
                        0.87
                ))
        );

        var detail = conversationService.getConversation(turn.conversationId());

        assertThat(detail.messages())
                .filteredOn(message -> message.role().equals("USER"))
                .singleElement()
                .satisfies(message -> assertThat(message.sources())
                        .singleElement()
                        .satisfies(source -> {
                            assertThat(source.filename()).isEqualTo("belge.pdf");
                            assertThat(source.chunkIndex()).isEqualTo(3);
                            assertThat(source.content())
                                    .isEqualTo("BELGE PARCASI");
                            assertThat(source.similarity()).isEqualTo(0.87);
                        }));

        assertThat(detail.messages())
                .filteredOn(message -> message.role().equals("ASSISTANT"))
                .allSatisfy(message -> assertThat(message.sources()).isEmpty());
    }
}
