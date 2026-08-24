package com.example.aicomparator.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.Conversation;
import com.example.aicomparator.entity.Message;
import com.example.aicomparator.repository.ConversationRepository;
import com.example.aicomparator.repository.MessageRepository;

@Service
public class ConversationService {

    private static final int TITLE_MAX_LENGTH = 80;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public CompareResponse saveComparison(
            String userContent,
            List<AiResponse> aiResponses
    ) {
        Conversation conversation = conversationRepository.save(
                new Conversation(createTitle(userContent))
        );

        Message userMessage = messageRepository.save(
                Message.createUserMessage(
                        conversation,
                        null,
                        userContent
                )
        );

        List<Message> assistantMessages = aiResponses.stream()
                .map(response -> Message.createAssistantMessage(
                        conversation,
                        userMessage,
                        AiProviderType.valueOf(response.provider()),
                        response.content()
                ))
                .toList();

        List<Message> savedAssistantMessages =
                messageRepository.saveAll(assistantMessages);

        List<AiResponse> savedResponses = savedAssistantMessages.stream()
                .map(message -> new AiResponse(
                        message.getId(),
                        message.getProvider().name(),
                        message.getContent()
                ))
                .toList();

        return new CompareResponse(
                conversation.getId(),
                userMessage.getId(),
                savedResponses
        );
    }

    private String createTitle(String userContent) {
        String normalizedContent = userContent
                .trim()
                .replaceAll("\\s+", " ");

        if (normalizedContent.length() <= TITLE_MAX_LENGTH) {
            return normalizedContent;
        }

        return normalizedContent.substring(0, TITLE_MAX_LENGTH - 3) + "...";
    }
}