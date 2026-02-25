-- V3__add_usage_metering.sql
-- Add usage metering tables for MAU tracking and authentication counting per Relying Party

-- Table: monthly_active_users
-- Tracks unique active users per relying party per month
-- Each row represents one user active in one RP in one specific month
CREATE TABLE monthly_active_users (
    relying_party_id UUID NOT NULL REFERENCES relying_parties(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month DATE NOT NULL,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    PRIMARY KEY (relying_party_id, user_id, month)
);

-- Indexes for efficient dashboard queries
CREATE INDEX idx_monthly_active_users_rp_month ON monthly_active_users(relying_party_id, month);
CREATE INDEX idx_monthly_active_users_month ON monthly_active_users(month);

-- Table: rp_monthly_counters
-- Tracks total authentication count per relying party per month
CREATE TABLE rp_monthly_counters (
    relying_party_id UUID NOT NULL REFERENCES relying_parties(id) ON DELETE CASCADE,
    month DATE NOT NULL,
    auth_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (relying_party_id, month)
);

-- Index for dashboard queries
CREATE INDEX idx_rp_monthly_counters_month ON rp_monthly_counters(month);
