package com.example.aicomparator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import com.example.aicomparator.entity.Conversation;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {
        List<Conversation> findAllByOrderByUpdatedAtDescIdDesc();
}