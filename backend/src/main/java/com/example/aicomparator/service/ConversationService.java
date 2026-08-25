package com.example.aicomparator.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.aicomparator.dto.ConversationDetailResponse;
import com.example.aicomparator.dto.ConversationSummaryResponse;
import com.example.aicomparator.dto.MessageHistoryResponse;
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

    private static final Logger log =
            LoggerFactory.getLogger(ConversationService.class);

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

        log.info("Yeni konuşma oluşturuldu: id={}", conversation.getId());

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
    public UserTurnResult startComparison(String userContent) {
        Conversation conversation = conversationRepository.save(
                new Conversation(createTitle(userContent))
        );

        log.info("Yeni konuşma oluşturuldu: id={}", conversation.getId());

        Message userMessage = messageRepository.save(
                Message.createUserMessage(
                        conversation,
                        null,
                        userContent
                )
        );

        return new UserTurnResult(conversation.getId(), userMessage.getId());
    }

    @Transactional
    public UserTurnResult startContinuation(
            Long conversationId,
            String userContent
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

        return new UserTurnResult(conversation.getId(), userMessage.getId());
    }

    public record UserTurnResult(Long conversationId, Long userMessageId) {
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

        log.debug(
                "Aktif mesaj değişti: conversationId={}, messageId={}, provider={}",
                conversationId,
                messageId,
                selectedMessage.getProvider()
        );

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
                .filter(response -> response.error() == null)
                .map(response -> Message.createAssistantMessage(
                        conversation,
                        userMessage,
                        AiProviderType.valueOf(response.provider()),
                        response.content()
                ))
                .toList();

        List<Message> savedAssistantMessages =
                messageRepository.saveAll(assistantMessages);

        List<AiResponse> savedResponses = aiResponses.stream()
                .map(response -> {
                    if (response.error() != null) {
                        return response;
                    }

                    Message savedMessage = savedAssistantMessages.stream()
                            .filter(message -> message.getProvider().name()
                                    .equals(response.provider()))
                            .findFirst()
                            .orElseThrow();

                    return AiResponse.success(
                            savedMessage.getId(),
                            savedMessage.getProvider().name(),
                            savedMessage.getContent()
                    );
                })
                .toList();

        return new CompareResponse(
                conversation.getId(),
                userMessage.getId(),
                savedResponses
        );
    }

    @Transactional(readOnly = true)
    public String buildPromptForUserMessage(
            Long conversationId,
            Long userMessageId
    ) {
        Message userMessage = messageRepository
                .findByIdAndConversation_Id(userMessageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Kullanıcı mesajı bulunamadı."
                ));

        if (userMessage.getRole() != MessageRole.USER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yalnızca kullanıcı mesajı yeniden denenebilir."
            );
        }

        return buildBranchPrompt(userMessage);
    }

    @Transactional
    public AiResponse saveRetriedResponse(
            Long conversationId,
            Long userMessageId,
            AiResponse response
    ) {
        Message userMessage = messageRepository
                .findByIdAndConversation_Id(userMessageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Kullanıcı mesajı bulunamadı."
                ));

        if (userMessage.getRole() != MessageRole.USER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yanıt yalnızca bir kullanıcı mesajına bağlanabilir."
            );
        }

        Message savedMessage = messageRepository.save(
                Message.createAssistantMessage(
                        userMessage.getConversation(),
                        userMessage,
                        AiProviderType.valueOf(response.provider()),
                        response.content()
                )
        );

        return AiResponse.success(
                savedMessage.getId(),
                savedMessage.getProvider().name(),
                savedMessage.getContent()
        );
    }

    private String buildBranchPrompt(Message lastMessage) {
        List<Message> activeBranch = new ArrayList<>();
        Message currentMessage = lastMessage;

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

        prompt.append("ASSISTANT:");
        return prompt.toString();
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
    @Transactional(readOnly = true)
public List<ConversationSummaryResponse> getConversations() {
    return conversationRepository
            .findAllByOrderByUpdatedAtDesc()
            .stream()
            .map(conversation -> new ConversationSummaryResponse(
                    conversation.getId(),
                    conversation.getTitle(),
                    conversation.getActiveMessage() == null
                            ? null
                            : conversation.getActiveMessage().getId(),
                    conversation.getCreatedAt(),
                    conversation.getUpdatedAt()
            ))
            .toList();
}

@Transactional(readOnly = true)
public ConversationDetailResponse getConversation(Long conversationId) {
    Conversation conversation = findConversation(conversationId);

    List<MessageHistoryResponse> messages = messageRepository
            .findByConversation_IdOrderByCreatedAtAsc(conversationId)
            .stream()
            .map(message -> new MessageHistoryResponse(
                    message.getId(),
                    message.getParentMessage() == null
                            ? null
                            : message.getParentMessage().getId(),
                    message.getRole().name(),
                    message.getProvider() == null
                            ? null
                            : message.getProvider().name(),
                    message.getContent(),
                    message.getCreatedAt()
            ))
            .toList();

    return new ConversationDetailResponse(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getActiveMessage() == null
                    ? null
                    : conversation.getActiveMessage().getId(),
            conversation.getCreatedAt(),
            conversation.getUpdatedAt(),
            messages
    );
}
}
