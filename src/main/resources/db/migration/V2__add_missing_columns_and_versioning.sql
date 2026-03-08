-- Flyway Migration V2: Add missing columns for state tracking, optimistic locking, and webhook retry

-- ==================== Orders: Add missing state tracking columns ====================
ALTER TABLE orders ADD COLUMN IF NOT EXISTS previous_state VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS state_changed_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ==================== Transactions: Add optimistic locking ====================
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ==================== Webhook Events: Add retry support ====================
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 3;

CREATE INDEX IF NOT EXISTS idx_webhook_events_retry
    ON webhook_events(status, next_retry_at)
    WHERE status = 'failed' AND retry_count < max_retries;

-- ==================== Subscriptions: Add version for optimistic locking ====================
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

