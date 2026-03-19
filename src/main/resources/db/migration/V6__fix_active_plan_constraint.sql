-- ============================================================
-- V6: Fix study_plans unique constraint to only apply to ACTIVE plans
-- The old constraint prevented multiple plans (even ABANDONED ones)
-- for the same user+product. Replace with a partial unique index.
-- ============================================================

ALTER TABLE study_plans DROP CONSTRAINT IF EXISTS uq_active_plan;

CREATE UNIQUE INDEX uq_active_plan
    ON study_plans (tenant_id, keycloak_user_id, product_code)
    WHERE status = 'ACTIVE';
