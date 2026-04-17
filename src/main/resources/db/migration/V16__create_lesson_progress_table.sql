-- V16: Create lesson_progress table for tracking lesson reading completion per user per lesson.

CREATE TABLE lesson_progress (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    keycloak_user_id     UUID         NOT NULL,
    product_code         VARCHAR(50)  NOT NULL,
    topic_code           VARCHAR(50)  NOT NULL,
    lesson_slug          VARCHAR(100) NOT NULL,
    is_completed         BOOLEAN      NOT NULL DEFAULT false,
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    time_spent_seconds   INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    version              INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_lesson_progress_user_slug
        UNIQUE (tenant_id, keycloak_user_id, product_code, lesson_slug),
    CONSTRAINT ck_lesson_progress_time_positive CHECK (time_spent_seconds >= 0)
);

-- Row-Level Security
ALTER TABLE lesson_progress ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_lesson_progress ON lesson_progress
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

CREATE POLICY tenant_isolation_lesson_progress_insert ON lesson_progress
    FOR INSERT WITH CHECK (tenant_id = current_setting('app.current_tenant')::UUID);

-- Performance indexes
CREATE INDEX idx_lesson_progress_lookup
    ON lesson_progress (tenant_id, keycloak_user_id, product_code);

CREATE INDEX idx_lesson_progress_topic_lookup
    ON lesson_progress (tenant_id, keycloak_user_id, product_code, topic_code);
