-- OAuth provider columns: support social login (Google, GitHub, etc.).
-- Both nullable: existing rows remain local (form-login) accounts.
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

-- A single (provider, provider_id) pair must be unique; null pairs (local accounts) are allowed.
CREATE UNIQUE INDEX IF NOT EXISTS uniq_users_provider_provider_id
    ON users(provider, provider_id)
    WHERE provider IS NOT NULL;

-- Password becomes nullable: OAuth-created accounts have no local password.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
