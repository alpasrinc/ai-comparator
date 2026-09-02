package com.example.aicomparator.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Bir kullanıcı turunda kullanılan kaynak parçası.
 *
 * <p>Kullanıcı mesajına bağlı, asistan mesajına değil: aynı parçalar üç
 * sağlayıcıya da gidiyor, bilgi tura ait.
 */
@Entity
@Table(name = "message_sources")
public class MessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chunk_id", nullable = false)
    private DocumentChunk chunk;

    @Column(nullable = false)
    private double similarity;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    protected MessageSource() {
    }

    public MessageSource(
            Message message,
            DocumentChunk chunk,
            double similarity,
            int rankOrder
    ) {
        this.message = Objects.requireNonNull(message);
        this.chunk = Objects.requireNonNull(chunk);
        this.similarity = similarity;
        this.rankOrder = rankOrder;
    }

    public Long getId() {
        return id;
    }

    public Message getMessage() {
        return message;
    }

    public DocumentChunk getChunk() {
        return chunk;
    }

    public double getSimilarity() {
        return similarity;
    }

    public int getRankOrder() {
        return rankOrder;
    }
}
