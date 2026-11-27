-- ROLE-8: a tenant admin can switch individual permissions off (and back on) for a role. The
-- built-in role matrix is the ceiling: a row here removes one of the role's default permissions
-- in that tenant; no row = the default. The tenant admin role itself can't be restricted.

CREATE TABLE tenant_role_permission_revocations (
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id),
    role        VARCHAR(40)  NOT NULL,
    permission  VARCHAR(60)  NOT NULL,
    revoked_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    revoked_at  TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, role, permission)
);
