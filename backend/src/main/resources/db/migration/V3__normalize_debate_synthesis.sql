INSERT INTO debate_messages (
    debate_id,
    round_number,
    provider,
    role,
    content,
    created_at
)
SELECT
    debate.id,
    NULL,
    debate.synthesizer_provider,
    'SYNTHESIS',
    debate.final_answer,
    debate.updated_at
FROM debates debate
WHERE debate.final_answer IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM debate_messages message
      WHERE message.debate_id = debate.id
        AND message.role = 'SYNTHESIS'
  );

ALTER TABLE debates DROP COLUMN final_answer;
