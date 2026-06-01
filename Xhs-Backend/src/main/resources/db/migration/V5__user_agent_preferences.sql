ALTER TABLE app.users ADD COLUMN IF NOT EXISTS agent_preferences JSONB NOT NULL DEFAULT '{}'::jsonb;
