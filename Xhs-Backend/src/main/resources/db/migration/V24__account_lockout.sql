-- 账号锁定：连续密码错误后锁定 15 分钟

SET search_path TO app, kb, public;

ALTER TABLE app.users
    ADD COLUMN failed_attempts  INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until     TIMESTAMPTZ;
