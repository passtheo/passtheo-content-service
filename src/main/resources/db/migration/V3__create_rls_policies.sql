-- ============================================================
-- Row-Level Security for tenant isolation
-- ============================================================

ALTER TABLE study_sessions       ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_answers      ENABLE ROW LEVEL SECURITY;
ALTER TABLE question_progress    ENABLE ROW LEVEL SECURITY;
ALTER TABLE topic_progress       ENABLE ROW LEVEL SECURITY;
ALTER TABLE domain_progress      ENABLE ROW LEVEL SECURITY;
ALTER TABLE streaks              ENABLE ROW LEVEL SECURITY;
ALTER TABLE earned_achievements  ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_attempts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_answers         ENABLE ROW LEVEL SECURITY;
ALTER TABLE readiness_snapshots  ENABLE ROW LEVEL SECURITY;
ALTER TABLE question_difficulty  ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_plans          ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_plan_days      ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events        ENABLE ROW LEVEL SECURITY;

-- Tenant isolation policies (USING for SELECT/UPDATE/DELETE, WITH CHECK for INSERT)

CREATE POLICY tenant_isolation_sessions ON study_sessions
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_session_answers ON session_answers
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_question_progress ON question_progress
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_topic_progress ON topic_progress
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_domain_progress ON domain_progress
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_streaks ON streaks
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_earned_achievements ON earned_achievements
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_exam_attempts ON exam_attempts
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_exam_answers ON exam_answers
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_readiness_snapshots ON readiness_snapshots
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_question_difficulty ON question_difficulty
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_study_plans ON study_plans
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_study_plan_days ON study_plan_days
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

CREATE POLICY tenant_isolation_outbox_events ON outbox_events
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);
