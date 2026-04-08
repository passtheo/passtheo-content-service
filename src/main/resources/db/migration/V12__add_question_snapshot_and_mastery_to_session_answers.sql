-- V12: Add question snapshot (JSONB) and mastery tracking to session_answers
-- for the practice session breakdown screen.

ALTER TABLE session_answers
    ADD COLUMN question_snapshot JSONB,
    ADD COLUMN previous_mastery_level VARCHAR(20),
    ADD COLUMN new_mastery_level VARCHAR(20),
    ADD COLUMN domain_code VARCHAR(100),
    ADD COLUMN domain_name VARCHAR(255);

-- Index for efficient breakdown queries by session
CREATE INDEX idx_session_answers_breakdown
    ON session_answers (session_id, question_order);
