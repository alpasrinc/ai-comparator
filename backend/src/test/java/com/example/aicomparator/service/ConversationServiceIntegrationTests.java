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
}