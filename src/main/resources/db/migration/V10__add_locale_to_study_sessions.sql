-- ============================================================
-- V10: Add locale column to study_sessions
-- The session owns its content locale — set once at creation from the
-- start-session request and used for all subsequent Strapi fetches.
-- This prevents mixed-language content when submitAnswer is called
-- without a locale parameter (which defaults to 'nl' at the HTTP layer).
-- ============================================================

ALTER TABLE study_sessions
    ADD COLUMN locale VARCHAR(10) NOT NULL DEFAULT 'nl';

COMMENT ON COLUMN study_sessions.locale IS
    'Content locale for this session (nl, en, ar, tr, pl). Set at session creation; never changes.';
