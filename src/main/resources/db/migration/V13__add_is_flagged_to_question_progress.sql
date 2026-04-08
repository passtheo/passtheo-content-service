-- V13: Add is_flagged to question_progress for user-flagged questions

ALTER TABLE question_progress
    ADD COLUMN is_flagged BOOLEAN NOT NULL DEFAULT false;

-- Partial index for efficient flagged question queries
CREATE INDEX idx_question_progress_flagged
    ON question_progress (keycloak_user_id, product_code, is_flagged)
    WHERE is_flagged = true;
