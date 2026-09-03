package com.example.aicomparator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aicomparator.entity.DocumentChunk;

public interface DocumentChunkRepository
        extends JpaRepository<DocumentChunk, Long> {

    @Query("""
            SELECT chunk FROM DocumentChunk chunk
            JOIN FETCH chunk.document document
            WHERE document.conversation.id = :conversationId
            ORDER BY document.id ASC, chunk.chunkIndex ASC
            """)
    List<DocumentChunk> findByConversationId(
            @Param("conversationId") Long conversationId);
}
