-- V2: Create feedback table for native-correction loop (US-06).
-- Captures 👍/👎 and optional suggested corrections from users.
-- Success metric: ≥ 100 corrections in month 1.

CREATE TABLE feedback (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          VARCHAR(100) NOT NULL,
    translation_id      VARCHAR(100),
    source_text         TEXT,
    translated_text     TEXT,
    type                VARCHAR(20) NOT NULL,    -- THUMBS_UP, THUMBS_DOWN, CORRECTION_PROPOSED
    suggested_correction TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_type       ON feedback (type);
CREATE INDEX idx_feedback_session_id ON feedback (session_id);
