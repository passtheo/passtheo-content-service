-- ============================================================
-- Outbox Events table for reliable Kafka publishing
-- ============================================================

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMP,
    failed_at       TIMESTAMP,
    error_message   TEXT
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at)
    WHERE status = 'PENDING';
