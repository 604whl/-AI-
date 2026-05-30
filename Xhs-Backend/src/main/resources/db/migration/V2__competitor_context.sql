ALTER TABLE app.analysis_task
    ADD COLUMN IF NOT EXISTS competitor_context JSONB;
