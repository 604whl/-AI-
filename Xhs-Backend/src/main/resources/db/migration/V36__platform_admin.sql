-- V36: platform_admin table (platform super admin)
--
-- 用户表 users.id 为 BIGSERIAL (BIGINT)，外键类型必须完全匹配。
-- ON DELETE RESTRICT：平台权限不可随用户删除自动失效，需管理员显式撤销，
--   防止误删后平台管理员继承关系丢失。
CREATE TABLE app.platform_admin (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app.users(id) ON DELETE RESTRICT,
    role VARCHAR(32) NOT NULL DEFAULT 'PLATFORM_ADMIN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ NULL,
    notes TEXT NULL
);

CREATE INDEX idx_platform_admin_user ON app.platform_admin(user_id) WHERE enabled = TRUE;

COMMENT ON TABLE app.platform_admin IS 'Platform super admin (cross-workspace global management)';
