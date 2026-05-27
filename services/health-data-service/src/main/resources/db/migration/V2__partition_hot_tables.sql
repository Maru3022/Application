-- V2: Partition hot tables by user_id hash for horizontal scalability.
--
-- At millions of users, sleep_entries, water_entries, and activity_entries
-- grow unboundedly. Hash partitioning by user_id distributes rows evenly
-- across 8 partitions, keeping each partition's index small and queries fast.
--
-- NOTE: PostgreSQL requires the parent table to be empty before converting to
-- partitioned. This migration is safe on a fresh database. For an existing
-- database with data, run the data migration script in docs/OPERATIONS.md first.

-- ── sleep_entries ────────────────────────────────────────────────────────────
ALTER TABLE sleep_entries RENAME TO sleep_entries_old;

CREATE TABLE sleep_entries (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    sleep_start   TIMESTAMPTZ  NOT NULL,
    sleep_end     TIMESTAMPTZ  NOT NULL,
    duration_min  INTEGER,
    quality       INTEGER      CHECK (quality BETWEEN 1 AND 5),
    notes         TEXT,
    source        VARCHAR(30)  DEFAULT 'manual',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
) PARTITION BY HASH (user_id);

CREATE TABLE sleep_entries_p0 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE sleep_entries_p1 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE sleep_entries_p2 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE sleep_entries_p3 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE sleep_entries_p4 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE sleep_entries_p5 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE sleep_entries_p6 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE sleep_entries_p7 PARTITION OF sleep_entries FOR VALUES WITH (MODULUS 8, REMAINDER 7);

CREATE INDEX ON sleep_entries (user_id, sleep_start DESC);

-- Migrate existing data (explicit column list avoids mismatch errors)
INSERT INTO sleep_entries (id, user_id, sleep_start, sleep_end, duration_min, quality, notes, source, created_at)
SELECT id, user_id, sleep_start, sleep_end, duration_min, quality, notes, source, created_at
FROM sleep_entries_old;

DROP TABLE sleep_entries_old;

-- ── water_entries ────────────────────────────────────────────────────────────
ALTER TABLE water_entries RENAME TO water_entries_old;

CREATE TABLE water_entries (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    amount_ml    INTEGER     NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY HASH (user_id);

CREATE TABLE water_entries_p0 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE water_entries_p1 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE water_entries_p2 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE water_entries_p3 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE water_entries_p4 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE water_entries_p5 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE water_entries_p6 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE water_entries_p7 PARTITION OF water_entries FOR VALUES WITH (MODULUS 8, REMAINDER 7);

CREATE INDEX ON water_entries (user_id, recorded_at DESC);

INSERT INTO water_entries (id, user_id, amount_ml, recorded_at)
SELECT id, user_id, amount_ml, recorded_at
FROM water_entries_old;

DROP TABLE water_entries_old;

-- ── activity_entries ─────────────────────────────────────────────────────────
ALTER TABLE activity_entries RENAME TO activity_entries_old;

CREATE TABLE activity_entries (
    id               UUID    NOT NULL DEFAULT gen_random_uuid(),
    user_id          UUID    NOT NULL,
    date             DATE    NOT NULL,
    steps            INTEGER DEFAULT 0,
    calories_burned  INTEGER DEFAULT 0,
    active_minutes   INTEGER DEFAULT 0,
    distance_m       INTEGER DEFAULT 0,
    source           VARCHAR(30),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, date)
) PARTITION BY HASH (user_id);

CREATE TABLE activity_entries_p0 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE activity_entries_p1 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE activity_entries_p2 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE activity_entries_p3 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE activity_entries_p4 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE activity_entries_p5 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE activity_entries_p6 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE activity_entries_p7 PARTITION OF activity_entries FOR VALUES WITH (MODULUS 8, REMAINDER 7);

CREATE INDEX ON activity_entries (user_id, date DESC);

INSERT INTO activity_entries (id, user_id, date, steps, calories_burned, active_minutes, distance_m, source, created_at)
SELECT id, user_id, date, steps, calories_burned, active_minutes, distance_m, source, created_at
FROM activity_entries_old;

DROP TABLE activity_entries_old;
