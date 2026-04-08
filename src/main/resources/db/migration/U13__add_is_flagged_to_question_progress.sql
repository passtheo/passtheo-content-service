-- U13: Undo V13 - remove is_flagged from question_progress

DROP INDEX IF EXISTS idx_question_progress_flagged;

ALTER TABLE question_progress
    DROP COLUMN IF EXISTS is_flagged;
