package com.example.aicomparator.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.dto.ActiveMessageResponse;
import com.example.aicomparator.dto.AiResponse;
import com.example.aicomparator.dto.CompareResponse;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.Conversation;
import com.example.aicomparator.entity.Message;
import com.example.aicomparator.entity.MessageRole;
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

        return saveAssistantResponses(
                conversation,
                userMessage,
                aiResponses
        );
    }

    @Transactional
    public ActiveMessageResponse selectActiveMessage(
            Long conversationId,
            Long messageId
    ) {
        Conversation conversation = findConversation(conversationId);

        Message selectedMessage = messageRepository
                .findByIdAndConversation_Id(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Seçilen mesaj bu konuşmada bulunamadı."
                ));

        if (selectedMessage.getRole() != MessageRole.ASSISTANT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yalnızca bir AI cevabı seçilebilir."
            );
        }

        conversation.selectActiveMessage(selectedMessage);

        return new ActiveMessageResponse(
                conversation.getId(),
                selectedMessage.getId(),
                selectedMessage.getProvider().name()
        );
    }

    @Transactional(readOnly = true)
    public String buildActiveContextPrompt(
            Long conversationId,
            String newUserContent
    ) {
        Conversation conversation = findConversation(conversationId);
        Message currentMessage = conversation.getActiveMessage();

        if (currentMessage == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Devam etmeden önce bir AI cevabı seçilmelidir."
            );
        }

        List<Message> activeBranch = new ArrayList<>();

        while (currentMessage != null) {
            activeBranch.add(currentMessage);
            currentMessage = currentMessage.getParentMessage();
        }

        Collections.reverse(activeBranch);

        StringBuilder prompt = new StringBuilder(
                "Aşağıdaki konuşmanın aktif dalını dikkate alarak "
                        + "son kullanıcı mesajını yanıtla.\n\n"
        );

        for (Message message : activeBranch) {
            prompt.append(message.getRole().name())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n\n");
        }

        prompt.append("USER: ")
                .append(newUserContent)
                .append("\n\nASSISTANT:");

        return prompt.toString();
    }

    @Transactional
    public CompareResponse saveContinuation(
            Long conversationId,
            String userContent,
            List<AiResponse> aiResponses
    ) {
        Conversation conversation = findConversation(conversationId);
        Message activeMessage = conversation.getActiveMessage();

        if (activeMessage == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Devam etmeden önce bir AI cevabı seçilmelidir."
            );
        }

        Message userMessage = messageRepository.save(
                Message.createUserMessage(
                        conversation,
                        activeMessage,
                        userContent
                )
        );

        return saveAssistantResponses(
                conversation,
                userMessage,
                aiResponses
        );
    }

    private CompareResponse saveAssistantResponses(
            Conversation conversation,
            Message userMessage,
            List<AiResponse> aiResponses
    ) {
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

    private Conversation findConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Konuşma bulunamadı."
                ));
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