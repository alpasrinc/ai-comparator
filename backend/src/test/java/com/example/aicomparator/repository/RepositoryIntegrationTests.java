package com.example.aicomparator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.Conversation;
import com.example.aicomparator.entity.Message;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class RepositoryIntegrationTests {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndReadConversationBranch() {
        Conversation conversation = new Conversation("Java nedir?");

        conversationRepository.save(conversation);

        Message userMessage = Message.createUserMessage(
            conversation,
            null,
            "Java nedir?"
        );

        messageRepository.save(userMessage);

        Message assistantMessage = Message.createAssistantMessage(
            conversation,
            userMessage,
            AiProviderType.OPENAI,
            "Java, nesne yönelimli bir programlama dilidir."
        );

        messageRepository.save(assistantMessage);

        conversation.selectActiveMessage(assistantMessage);

        entityManager.flush();

        Long conversationId = conversation.getId();
        Long userMessageId = userMessage.getId();
        Long assistantMessageId = assistantMessage.getId();

        entityManager.clear();

        Conversation savedConversation = conversationRepository
            .findById(conversationId)
            .orElseThrow();

        List<Message> conversationMessages = messageRepository
            .findByConversation_IdOrderByCreatedAtAsc(conversationId);

        List<Message> childMessages = messageRepository
            .findByParentMessage_IdOrderByCreatedAtAsc(userMessageId);

        assertThat(savedConversation.getTitle())
            .isEqualTo("Java nedir?");

        assertThat(savedConversation.getActiveMessage().getId())
            .isEqualTo(assistantMessageId);

        assertThat(conversationMessages)
            .hasSize(2);

        assertThat(childMessages)
            .hasSize(1);

        assertThat(childMessages.getFirst().getProvider())
            .isEqualTo(AiProviderType.OPENAI);
    }
}