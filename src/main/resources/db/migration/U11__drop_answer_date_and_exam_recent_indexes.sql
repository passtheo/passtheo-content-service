-- U11: Undo V11 - drop performance indexes added for date-range queries

DROP INDEX IF EXISTS idx_answer_date;
DROP INDEX IF EXISTS idx_exam_user_recent;
