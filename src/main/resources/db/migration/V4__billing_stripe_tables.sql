-- V4__billing_stripe_tables.sql
-- Add Stripe billing tables for donation and subscription management

-- Table: billing_customers
-- Maps nullProfile users to Stripe customers (one-to-one relationship)
CREATE TABLE billing_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    stripe_customer_id TEXT NOT NULL UNIQUE,
    email TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for billing_customers
CREATE INDEX idx_billing_customers_user_id ON billing_customers(user_id);
CREATE INDEX idx_billing_customers_stripe_customer_id ON billing_customers(stripe_customer_id);

-- Table: billing_subscriptions
-- Tracks Stripe subscription status (future-proof for subscription model)
CREATE TABLE billing_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stripe_subscription_id TEXT NOT NULL UNIQUE,
    stripe_price_id TEXT,
    stripe_product_id TEXT,
    status TEXT NOT NULL DEFAULT 'unknown',
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    latest_invoice_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for billing_subscriptions
CREATE INDEX idx_billing_subscriptions_user_id ON billing_subscriptions(user_id);
CREATE INDEX idx_billing_subscriptions_stripe_subscription_id ON billing_subscriptions(stripe_subscription_id);
CREATE INDEX idx_billing_subscriptions_status ON billing_subscriptions(status);
CREATE INDEX idx_billing_subscriptions_created_at ON billing_subscriptions(created_at DESC);

-- Table: billing_payments
-- Tracks individual payment transactions (donations, invoices, etc.)
CREATE TABLE billing_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL DEFAULT 'donation',
    status TEXT NOT NULL DEFAULT 'unknown',
    amount BIGINT NOT NULL CHECK (amount >= 0),
    currency TEXT NOT NULL,
    stripe_payment_intent_id TEXT UNIQUE,
    stripe_invoice_id TEXT UNIQUE,
    stripe_charge_id TEXT UNIQUE,
    stripe_price_id TEXT,
    stripe_product_id TEXT,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for billing_payments
CREATE INDEX idx_billing_payments_user_id ON billing_payments(user_id);
CREATE INDEX idx_billing_payments_type ON billing_payments(type);
CREATE INDEX idx_billing_payments_status ON billing_payments(status);
CREATE INDEX idx_billing_payments_created_at ON billing_payments(created_at DESC);
CREATE INDEX idx_billing_payments_stripe_payment_intent_id ON billing_payments(stripe_payment_intent_id);
CREATE INDEX idx_billing_payments_stripe_invoice_id ON billing_payments(stripe_invoice_id);

-- Table: billing_events
-- Stores Stripe webhook events for idempotency and audit trail
CREATE TABLE billing_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    payload JSONB
);

-- Indexes for billing_events
CREATE INDEX idx_billing_events_stripe_event_id ON billing_events(stripe_event_id);
CREATE INDEX idx_billing_events_type ON billing_events(type);
CREATE INDEX idx_billing_events_received_at ON billing_events(received_at DESC);
CREATE INDEX idx_billing_events_processed_at ON billing_events(processed_at) WHERE processed_at IS NULL;

-- Function to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for billing_customers.updated_at
CREATE TRIGGER trigger_billing_customers_updated_at
    BEFORE UPDATE ON billing_customers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for billing_subscriptions.updated_at
CREATE TRIGGER trigger_billing_subscriptions_updated_at
    BEFORE UPDATE ON billing_subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
