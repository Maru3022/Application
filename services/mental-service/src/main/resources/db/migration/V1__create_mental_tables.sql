-- Mental Service Flyway Migration
CREATE TABLE mood_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    mood_score  INTEGER CHECK (mood_score BETWEEN 1 AND 10),
    emotions    TEXT,
    note        TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE journal_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    content     TEXT NOT NULL,
    tags        TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE stress_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    level       INTEGER NOT NULL CHECK (level BETWEEN 1 AND 10),
    recorded_at TIMESTAMPTZ NOT NULL,
    notes       TEXT
);

CREATE TABLE meditations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    duration_min INT,
    category     VARCHAR(100),
    audio_url    VARCHAR(500)
);

CREATE TABLE breathing_sessions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    technique    VARCHAR(100) NOT NULL,
    duration_min INT NOT NULL,
    recorded_at  TIMESTAMPTZ
);
