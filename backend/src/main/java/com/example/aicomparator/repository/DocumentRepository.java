package com.example.aicomparator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aicomparator.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByConversation_IdOrderByIdAsc(Long conversationId);

    boolean existsByConversation_Id(Long conversationId);
}
