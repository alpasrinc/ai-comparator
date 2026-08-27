CREATE TABLE debates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic LONGTEXT NOT NULL,
    rounds INT NOT NULL,
    synthesizer_provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    final_answer LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE debate_participants (
    debate_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    PRIMARY KEY (debate_id, provider),
    CONSTRAINT fk_debate_participants_debate
        FOREIGN KEY (debate_id) REFERENCES debates (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE debate_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    debate_id BIGINT NOT NULL,
    round_number INT NULL,
    provider VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_debate_messages_debate
        FOREIGN KEY (debate_id) REFERENCES debates (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_debate_messages_debate_id ON debate_messages (debate_id);
CREATE INDEX idx_debates_updated_at ON debates (updated_at);
