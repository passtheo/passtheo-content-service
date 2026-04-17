-- U16: Drop lesson_progress table (undo V16).

DROP INDEX IF EXISTS idx_lesson_progress_topic_lookup;
DROP INDEX IF EXISTS idx_lesson_progress_lookup;

DROP POLICY IF EXISTS tenant_isolation_lesson_progress_insert ON lesson_progress;
DROP POLICY IF EXISTS tenant_isolation_lesson_progress ON lesson_progress;

DROP TABLE IF EXISTS lesson_progress;
