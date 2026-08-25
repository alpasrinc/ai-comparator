CREATE TABLE conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    active_message_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversations_active_message_id (active_message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    parent_message_id BIGINT NULL,
    role VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_parent_message
        FOREIGN KEY (parent_message_id) REFERENCES messages (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX idx_messages_parent_message_id ON messages (parent_message_id);
CREATE INDEX idx_conversations_updated_at ON conversations (updated_at);

ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_active_message
        FOREIGN KEY (active_message_id) REFERENCES messages (id);
