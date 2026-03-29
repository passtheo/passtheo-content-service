-- ============================================================
-- V8: Add version column to streaks (optimistic locking)
--     Add partition_key column to outbox_events (Kafka ordering)
-- ============================================================

-- Add JPA @Version column to streaks table
ALTER TABLE streaks
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- Add partition_key to outbox_events for user-ordered Kafka publishing
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS partition_key VARCHAR(100);
