-- Auth Service Flyway Migration
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    display_name  VARCHAR(100),
    timezone      VARCHAR(50) DEFAULT 'UTC',
    date_of_birth DATE,
    gender        VARCHAR(20),
    height_cm     NUMERIC(5,1),
    mfa_secret    VARCHAR(100),
    mfa_enabled   BOOLEAN DEFAULT FALSE,
    email_verified BOOLEAN DEFAULT FALSE,
    role          VARCHAR(20) DEFAULT 'USER',
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(500) UNIQUE NOT NULL,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(255) UNIQUE NOT NULL,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL,
    used        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(255) UNIQUE NOT NULL,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL,
    used        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
