package com.example.aicomparator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aicomparator.entity.MessageSource;

public interface MessageSourceRepository
        extends JpaRepository<MessageSource, Long> {

    @Query("""
            SELECT source FROM MessageSource source
            JOIN FETCH source.chunk chunk
            JOIN FETCH chunk.document
            WHERE source.message.id = :messageId
            ORDER BY source.rankOrder ASC
            """)
    List<MessageSource> findByMessageId(@Param("messageId") Long messageId);
}
