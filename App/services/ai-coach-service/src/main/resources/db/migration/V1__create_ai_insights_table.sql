-- AI Coach Service Flyway Migration
CREATE TABLE ai_insights (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    type       VARCHAR(50),
    content    TEXT NOT NULL,
    data_hash  VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_ai_insights_user ON ai_insights(user_id, type, created_at DESC);
