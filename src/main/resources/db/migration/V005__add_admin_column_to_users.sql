-- Add admin column to users table
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'users' AND column_name = 'admin') THEN
        ALTER TABLE users ADD COLUMN admin BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_admin ON users(admin);

COMMENT ON COLUMN users.admin IS 'Whether the user has admin privileges';
