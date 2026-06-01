-- Agent 对话与工具调用审计

CREATE TABLE app.chat_session (
    id              VARCHAR(32) PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES app.users(id),
    title           VARCHAR(128),
    persona         VARCHAR(32) DEFAULT 'agency',
    linked_task_id  VARCHAR(32) REFERENCES app.analysis_task(id),
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_session_user ON app.chat_session (user_id, updated_at DESC);

CREATE TABLE app.chat_message (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(32) NOT NULL REFERENCES app.chat_session(id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT,
    tool_calls      JSONB,
    tool_call_id    VARCHAR(64),
    tool_name       VARCHAR(64),
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_session ON app.chat_message (session_id, id);

CREATE TABLE app.agent_tool_log (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(32) NOT NULL,
    user_id         BIGINT NOT NULL,
    tool_name       VARCHAR(64) NOT NULL,
    input_json      JSONB,
    output_summary  TEXT,
    success         BOOLEAN NOT NULL,
    latency_ms      INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_tool_log_user_day ON app.agent_tool_log (user_id, created_at);
