-- U12: Undo V12 - remove question snapshot and mastery columns from session_answers

DROP INDEX IF EXISTS idx_session_answers_breakdown;

ALTER TABLE session_answers
    DROP COLUMN IF EXISTS question_snapshot,
    DROP COLUMN IF EXISTS previous_mastery_level,
    DROP COLUMN IF EXISTS new_mastery_level,
    DROP COLUMN IF EXISTS domain_code,
    DROP COLUMN IF EXISTS domain_name;
