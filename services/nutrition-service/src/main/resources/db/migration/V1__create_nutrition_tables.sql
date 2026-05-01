-- Nutrition Service Flyway Migration
CREATE TABLE foods (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    calories_per_100g DOUBLE PRECISION,
    protein_per_100g  DOUBLE PRECISION,
    carbs_per_100g    DOUBLE PRECISION,
    fat_per_100g      DOUBLE PRECISION,
    barcode           VARCHAR(50),
    source            VARCHAR(30) DEFAULT 'system',
    user_id           UUID
);
CREATE INDEX idx_foods_name ON foods(name);
CREATE INDEX idx_foods_barcode ON foods(barcode);

CREATE TABLE food_log_entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    food_id       UUID NOT NULL REFERENCES foods(id),
    weight_grams  DOUBLE PRECISION NOT NULL,
    meal_type     VARCHAR(20),
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_food_log_user ON food_log_entries(user_id, consumed_at DESC);
