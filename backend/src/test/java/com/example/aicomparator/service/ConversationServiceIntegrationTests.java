package com.example.aicomparator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.Message;
import com.example.aicomparator.entity.MessageRole;
import com.example.aicomparator.repository.ConversationRepository;
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
                    "Bir örnek verir misin?"
            );

    assertThat(contextPrompt)
            .contains(
                    "USER: Java nedir?",
                    "ASSISTANT: Seçilen Claude cevabı",
                    "USER: Bir örnek verir misin?"
            )
            .doesNotContain(
                    "OpenAI alternatif cevabı",
                    "Gemini alternatif cevabı"
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
}
