package com.example.aicomparator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aicomparator.entity.DebateMessage;

public interface DebateMessageRepository
        extends JpaRepository<DebateMessage, Long> {

    List<DebateMessage> findByDebateIdOrderByIdAsc(Long debateId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DebateMessage message "
            + "where message.debate.id = :debateId")
    int deleteByDebateId(@Param("debateId") Long debateId);
}
