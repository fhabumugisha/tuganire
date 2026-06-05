-- V1: Create golden_dictionary table for pre-validated phrase lookups.
-- This table stores native-validated FR→RW translations that bypass the LLM
-- pipeline (~40% of requests per PRD risk mitigation strategy).

CREATE TABLE golden_dictionary (
    id           BIGSERIAL PRIMARY KEY,
    source_text  TEXT NOT NULL,
    source_lang  VARCHAR(2) NOT NULL,
    target_text  TEXT NOT NULL,
    target_lang  VARCHAR(2) NOT NULL,
    alternatives TEXT[],                         -- variantes acceptables
    context      VARCHAR(100),                   -- "santé", "marché", "transport"...
    error_category VARCHAR(50),                  -- "INFIX_PRONOUN", "INVENTION", "PLURAL_RESPECT"...
    validated_by VARCHAR(100) NOT NULL,
    validated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    usage_count  INTEGER NOT NULL DEFAULT 0,
    score_avg    NUMERIC(3, 1)                   -- moyenne 👍/👎 collectée via feedback
);

CREATE INDEX idx_golden_source  ON golden_dictionary (source_lang, source_text);
CREATE INDEX idx_golden_context ON golden_dictionary (context);
