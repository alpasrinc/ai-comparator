-- RAG: konuşmaya bağlı belgeler, parçaları ve mesaj başına kullanılan kaynaklar.
CREATE TABLE documents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    chunk_count     INT NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_documents_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_documents_conversation ON documents (conversation_id);

CREATE TABLE document_chunks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id     BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding       LONGBLOB NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_chunks_document
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);

CREATE INDEX idx_chunks_document ON document_chunks (document_id);

CREATE TABLE message_sources (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT NOT NULL,
    chunk_id    BIGINT NOT NULL,
    similarity  DOUBLE NOT NULL,
    rank_order  INT NOT NULL,
    CONSTRAINT uq_message_sources UNIQUE (message_id, chunk_id),
    CONSTRAINT fk_sources_message
        FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_sources_chunk
        FOREIGN KEY (chunk_id) REFERENCES document_chunks (id) ON DELETE CASCADE
);

CREATE INDEX idx_message_sources_message ON message_sources (message_id);
