-- V15: Create user_xp table for experience point tracking per user per product.

CREATE TABLE user_xp (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    keycloak_user_id UUID         NOT NULL,
    product_code     VARCHAR(50)  NOT NULL,
    total_xp         INTEGER      NOT NULL DEFAULT 0,
    current_level    INTEGER      NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    version          INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_user_xp_user_product UNIQUE (tenant_id, keycloak_user_id, product_code),
    CONSTRAINT ck_user_xp_total_xp_positive CHECK (total_xp >= 0),
    CONSTRAINT ck_user_xp_level_positive CHECK (current_level >= 1)
);

-- Row-Level Security
ALTER TABLE user_xp ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_user_xp ON user_xp
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

CREATE POLICY tenant_isolation_user_xp_insert ON user_xp
    FOR INSERT WITH CHECK (tenant_id = current_setting('app.current_tenant')::UUID);

-- Performance index
CREATE INDEX idx_user_xp_lookup ON user_xp (tenant_id, keycloak_user_id, product_code);
