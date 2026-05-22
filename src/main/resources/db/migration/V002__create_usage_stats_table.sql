-- Create usage_stats table for freemium limit tracking
CREATE TABLE IF NOT EXISTS "usage_stats" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month DATE NOT NULL,
    sermon_count INTEGER NOT NULL DEFAULT 0,
    search_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_month UNIQUE(user_id, month)
);

-- Composite index for efficient user + month queries
CREATE INDEX IF NOT EXISTS idx_usage_stats_user_month ON usage_stats(user_id, month);

COMMENT ON TABLE usage_stats IS 'Tracks monthly usage statistics for freemium limit enforcement';
COMMENT ON COLUMN usage_stats.month IS 'First day of the month (format: YYYY-MM-01)';
COMMENT ON COLUMN usage_stats.sermon_count IS 'Number of sermons created this month';
COMMENT ON COLUMN usage_stats.search_count IS 'Number of searches performed this month';
