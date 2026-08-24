package com.example.aicomparator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aicomparator.entity.Conversation;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {
}