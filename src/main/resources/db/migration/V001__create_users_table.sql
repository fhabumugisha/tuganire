CREATE EXTENSION IF NOT EXISTS vector;

CREATE  TABLE IF NOT EXISTS "users" (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    stripe_customer_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP
);

CREATE  INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE  INDEX IF NOT EXISTS idx_users_stripe_customer ON users(stripe_customer_id);