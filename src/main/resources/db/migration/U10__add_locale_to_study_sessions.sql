-- ============================================================
-- U10: Undo V10 — drop locale column from study_sessions
-- ============================================================

ALTER TABLE study_sessions
    DROP COLUMN IF EXISTS locale;
