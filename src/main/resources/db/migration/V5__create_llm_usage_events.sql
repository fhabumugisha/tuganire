-- V5: Create llm_usage_events table for lightweight LLM usage / cost analytics.
-- Each row records a single LLM call (translation, etc.) with token counts and
-- the estimated cost computed at write time from configured per-model pricing.
-- Tracking is best-effort: a write failure must never break the originating call.

CREATE TABLE llm_usage_events (
    id                BIGSERIAL PRIMARY KEY,
    provider          VARCHAR(50)  NOT NULL,        -- "openai", "anthropic"...
    model             VARCHAR(100) NOT NULL,        -- "gpt-4o", "claude-haiku-4-5-..."
    feature           VARCHAR(50)  NOT NULL,        -- "translation"...
    prompt_tokens     INTEGER      NOT NULL DEFAULT 0,
    completion_tokens INTEGER      NOT NULL DEFAULT 0,
    estimated_cost    NUMERIC(12, 6) NOT NULL DEFAULT 0,  -- USD
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_usage_created  ON llm_usage_events (created_at);
CREATE INDEX idx_llm_usage_model    ON llm_usage_events (provider, model);
