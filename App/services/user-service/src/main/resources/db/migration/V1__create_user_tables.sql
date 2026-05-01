-- User Service Flyway Migration
CREATE TABLE user_profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID UNIQUE NOT NULL,
    display_name     VARCHAR(100),
    timezone         VARCHAR(50) DEFAULT 'UTC',
    date_of_birth    DATE,
    gender           VARCHAR(20),
    height_cm        NUMERIC(5,1),
    avatar_url       VARCHAR(500),
    subscription_plan VARCHAR(20) DEFAULT 'FREE',
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ
);

CREATE TABLE user_goals (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID UNIQUE NOT NULL,
    daily_steps      INT DEFAULT 10000,
    sleep_minutes    INT DEFAULT 480,
    water_ml         INT DEFAULT 2000,
    target_weight_kg NUMERIC(5,2),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);
