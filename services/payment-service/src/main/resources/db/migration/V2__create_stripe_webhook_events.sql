CREATE TABLE stripe_webhook_events (
    event_id        VARCHAR(64) PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
