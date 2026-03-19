-- ============================================================
-- Performance indexes for Content Service tables
-- ============================================================

-- Study Sessions
CREATE INDEX idx_session_user ON study_sessions(tenant_id, keycloak_user_id, status);
CREATE INDEX idx_session_abandoned ON study_sessions(status, last_activity_at)
    WHERE status = 'IN_PROGRESS';

-- Session Answers
CREATE INDEX idx_answer_session ON session_answers(session_id);
CREATE INDEX idx_answer_user_question ON session_answers(tenant_id, keycloak_user_id, strapi_question_id);

-- Question Progress
CREATE INDEX idx_progress_review ON question_progress(
    tenant_id, keycloak_user_id, product_code, next_review_at
) WHERE mastery_level != 'NEW';

CREATE INDEX idx_progress_new ON question_progress(
    tenant_id, keycloak_user_id, product_code, domain_code
) WHERE mastery_level = 'NEW';

CREATE INDEX idx_progress_mastery ON question_progress(
    tenant_id, keycloak_user_id, product_code, mastery_level
);

CREATE INDEX idx_progress_domain ON question_progress(
    tenant_id, keycloak_user_id, product_code, domain_code
);

-- Exam Attempts
CREATE INDEX idx_exam_user ON exam_attempts(tenant_id, keycloak_user_id, product_code);
CREATE INDEX idx_exam_passed ON exam_attempts(tenant_id, keycloak_user_id, passed);

-- Exam Answers
CREATE INDEX idx_exam_answer_attempt ON exam_answers(exam_attempt_id);

-- Readiness Snapshots
CREATE INDEX idx_readiness_user ON readiness_snapshots(
    tenant_id, keycloak_user_id, product_code, snapshot_date
);

-- Streaks
CREATE INDEX idx_streak_user ON streaks(tenant_id, keycloak_user_id, product_code);

-- Earned Achievements
CREATE INDEX idx_achievement_user ON earned_achievements(tenant_id, keycloak_user_id);

-- Question Difficulty
CREATE INDEX idx_difficulty_product ON question_difficulty(tenant_id, product_code);

-- Study Plans
CREATE INDEX idx_plan_user ON study_plans(tenant_id, keycloak_user_id, product_code, status);

-- Study Plan Days
CREATE INDEX idx_plan_day_date ON study_plan_days(plan_id, plan_date);
