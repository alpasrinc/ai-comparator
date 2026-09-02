package com.example.aicomparator.dto;

/** Retrieval sonucu; prompt kurucusuna ve frontend'e aynı tip gider. */
public record RetrievedChunk(
        Long chunkId,
        Long documentId,
        String filename,
        int chunkIndex,
        String content,
        double similarity
) {
}
