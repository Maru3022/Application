-- Health Data Service Flyway Migration
CREATE TABLE sleep_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    sleep_start TIMESTAMPTZ NOT NULL,
    sleep_end   TIMESTAMPTZ NOT NULL,
    duration_min INT,
    quality     INTEGER CHECK (quality BETWEEN 1 AND 5),
    notes       TEXT,
    source      VARCHAR(30),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_sleep_user_date ON sleep_entries(user_id, sleep_start DESC);

CREATE TABLE weight_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    weight_kg    NUMERIC(5,2) NOT NULL,
    body_fat_pct NUMERIC(4,1),
    recorded_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE water_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    amount_ml   INT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE activity_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    date            DATE NOT NULL,
    steps           INT DEFAULT 0,
    calories_burned INT DEFAULT 0,
    active_minutes  INT DEFAULT 0,
    distance_m      INT DEFAULT 0,
    source          VARCHAR(30),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, date)
);

CREATE TABLE symptom_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    symptom     VARCHAR(100) NOT NULL,
    intensity   INTEGER CHECK (intensity BETWEEN 1 AND 10),
    recorded_at TIMESTAMPTZ NOT NULL,
    notes       TEXT
);

CREATE TABLE cycle_entries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    period_start   DATE NOT NULL,
    period_end     DATE,
    cycle_length   INT,
    flow_intensity VARCHAR(20),
    symptoms       TEXT[],
    notes          TEXT
);
