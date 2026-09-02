-- Persist per-message prompt-cache accounting alongside the V4 token columns.
ALTER TABLE messages
    ADD COLUMN cache_read_tokens BIGINT NULL,
    ADD COLUMN cache_write_tokens BIGINT NULL;

ALTER TABLE debate_messages
    ADD COLUMN cache_read_tokens BIGINT NULL,
    ADD COLUMN cache_write_tokens BIGINT NULL;
