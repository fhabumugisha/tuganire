-- V7: A/B preference votes between models' Kinyarwanda transcript cleanups.
CREATE TABLE rw_model_votes (
    id             BIGSERIAL PRIMARY KEY,
    chosen_model   VARCHAR(100) NOT NULL,
    rejected_model VARCHAR(100),
    session_id     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_rw_votes_chosen_model ON rw_model_votes (chosen_model);
