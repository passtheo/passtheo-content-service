-- V14: Create question_reports table for user error reports on questions

CREATE TABLE question_reports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    strapi_question_id  VARCHAR(100) NOT NULL,
    report_type         VARCHAR(50)  NOT NULL
        CHECK (report_type IN ('INCORRECT_ANSWER', 'UNCLEAR_QUESTION', 'WRONG_IMAGE', 'WRONG_EXPLANATION', 'OTHER')),
    comment             TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- RLS
ALTER TABLE question_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_question_reports ON question_reports
    USING (tenant_id = current_setting('app.tenant_id')::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::UUID);

-- Indexes
CREATE INDEX idx_question_reports_question
    ON question_reports (strapi_question_id);

CREATE INDEX idx_question_reports_user
    ON question_reports (keycloak_user_id, created_at DESC);
