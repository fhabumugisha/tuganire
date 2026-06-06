-- V8: A/B preference votes between Kinyarwanda TTS voices (MMS+pauses vs OpenAI steered).
CREATE TABLE tts_voice_votes (
    id               BIGSERIAL PRIMARY KEY,
    chosen_variant   VARCHAR(100) NOT NULL,
    rejected_variant VARCHAR(100),
    session_id       VARCHAR(100),
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tts_votes_chosen_variant ON tts_voice_votes (chosen_variant);
