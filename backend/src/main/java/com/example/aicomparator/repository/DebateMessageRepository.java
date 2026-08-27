package com.example.aicomparator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aicomparator.entity.DebateMessage;

public interface DebateMessageRepository
        extends JpaRepository<DebateMessage, Long> {

    List<DebateMessage> findByDebateIdOrderByIdAsc(Long debateId);
}
