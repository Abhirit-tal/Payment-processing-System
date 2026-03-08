-- Flyway Migration V3: Add billing cycle tracking and next_billing_date for recurring billing scheduler

-- ==================== Subscriptions: Add billing cycle columns ====================
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS next_billing_date DATE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS total_billed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS last_billed_at TIMESTAMP;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS billing_failures INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_subscription_next_billing
    ON subscriptions(next_billing_date, status)
    WHERE status = 'active';

