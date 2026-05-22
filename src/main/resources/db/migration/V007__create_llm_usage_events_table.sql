CREATE TABLE llm_usage_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    feature VARCHAR(100),
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_llm_usage_user_date ON llm_usage_events(user_id, created_at DESC);
CREATE INDEX idx_llm_usage_date ON llm_usage_events(created_at DESC);
CREATE INDEX idx_llm_usage_model ON llm_usage_events(model);

COMMENT ON TABLE llm_usage_events IS 'Per-call LLM token usage and cost tracking';
