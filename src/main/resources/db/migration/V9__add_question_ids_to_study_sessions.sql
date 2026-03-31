-- ============================================================
-- V9: Add question_ids column to study_sessions
-- Stores the ordered list of strapi question IDs selected at session start
-- so the question sequence is fixed and deterministic throughout the session.
-- Prevents duplicate questions caused by re-running spaced repetition selection
-- (which shuffles new questions differently each time).
-- ============================================================

ALTER TABLE study_sessions
    ADD COLUMN question_ids TEXT;

COMMENT ON COLUMN study_sessions.question_ids IS
    'Comma-separated Strapi document IDs in session order, set at session start. NULL for legacy sessions.';
