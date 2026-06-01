ALTER TABLE app.analysis_report
    ADD COLUMN IF NOT EXISTS cover_analysis JSONB;
