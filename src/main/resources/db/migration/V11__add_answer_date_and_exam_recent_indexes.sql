-- V11: Add performance indexes for date-range queries
--
-- idx_answer_date: supports StreakService.findStudyDatesBetween() which filters
--   session_answers by answered_at date range to compute 7-day study activity.
--
-- idx_exam_user_recent: supports ExamAttemptRepository.countRecentExams() which
--   adds a completedAt > cutoff filter to the existing idx_exam_user index.
--   The existing idx_exam_user covers (tenant_id, keycloak_user_id, product_code)
--   but does not include completed_at, forcing a heap filter after the index scan.
--   Both indexes include tenant_id as leading column for efficient RLS filtering.

CREATE INDEX idx_answer_date ON session_answers(tenant_id, answered_at);

CREATE INDEX idx_exam_user_recent ON exam_attempts(tenant_id, keycloak_user_id, product_code, completed_at DESC);
