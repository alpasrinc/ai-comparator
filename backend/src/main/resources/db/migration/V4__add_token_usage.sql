-- Persist per-message token usage for compare and debate messages.
ALTER TABLE messages
    ADD COLUMN input_tokens BIGINT NULL,
    ADD COLUMN output_tokens BIGINT NULL;

ALTER TABLE debate_messages
    ADD COLUMN input_tokens BIGINT NULL,
    ADD COLUMN output_tokens BIGINT NULL;
