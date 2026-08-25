package com.example.aicomparator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aicomparator.entity.Message;

public interface MessageRepository
        extends JpaRepository<Message, Long> {

    List<Message> findByConversation_IdOrderByCreatedAtAsc(
        Long conversationId
    );

    List<Message> findByParentMessage_IdOrderByCreatedAtAsc(
        Long parentMessageId
    );

    Optional<Message> findByIdAndConversation_Id(
        Long messageId,
        Long conversationId
);
}