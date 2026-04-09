-- U15: Undo V15 — drop user_xp table.

DROP POLICY IF EXISTS tenant_isolation_user_xp_insert ON user_xp;
DROP POLICY IF EXISTS tenant_isolation_user_xp ON user_xp;
DROP INDEX IF EXISTS idx_user_xp_lookup;
DROP TABLE IF EXISTS user_xp;
