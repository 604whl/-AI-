-- 补齐 chat_message 列（V4 早期版本可能缺少以下字段）

ALTER TABLE app.chat_message ADD COLUMN IF NOT EXISTS tool_calls JSONB;
ALTER TABLE app.chat_message ADD COLUMN IF NOT EXISTS tool_call_id VARCHAR(64);
ALTER TABLE app.chat_message ADD COLUMN IF NOT EXISTS tool_name VARCHAR(64);
ALTER TABLE app.chat_message ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
