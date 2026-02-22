-- V1__initial_schema.sql
-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP
);

-- Create webauthn_credentials table
CREATE TABLE webauthn_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id VARCHAR(1024) NOT NULL UNIQUE,
    public_key_cose TEXT NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    aaguid VARCHAR(36),
    transports VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP
);

CREATE INDEX idx_webauthn_credentials_user_id ON webauthn_credentials(user_id);
CREATE INDEX idx_webauthn_credentials_credential_id ON webauthn_credentials(credential_id);

-- Create relying_parties table
CREATE TABLE relying_parties (
    id UUID PRIMARY KEY,
    rp_id VARCHAR(255) NOT NULL UNIQUE,
    rp_name VARCHAR(255) NOT NULL,
    sector_id VARCHAR(255) NOT NULL,
    branding_logo_url VARCHAR(512),
    branding_primary_color VARCHAR(7),
    branding_secondary_color VARCHAR(7),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    plan_tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMP NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_relying_parties_rp_id ON relying_parties(rp_id);
CREATE INDEX idx_relying_parties_created_by_user_id ON relying_parties(created_by_user_id);

-- Create redirect_uris table
CREATE TABLE redirect_uris (
    id UUID PRIMARY KEY,
    relying_party_id UUID NOT NULL REFERENCES relying_parties(id) ON DELETE CASCADE,
    uri VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (relying_party_id, uri)
);

CREATE INDEX idx_redirect_uris_relying_party_id ON redirect_uris(relying_party_id);
