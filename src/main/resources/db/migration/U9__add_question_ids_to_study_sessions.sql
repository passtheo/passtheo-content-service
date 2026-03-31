-- ============================================================
-- U9: Undo V9 — remove question_ids column from study_sessions
-- ============================================================

ALTER TABLE study_sessions
    DROP COLUMN IF EXISTS question_ids;
