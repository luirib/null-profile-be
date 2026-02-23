-- V2__add_passkey_name.sql
-- Add name column to webauthn_credentials table for user-friendly passkey identification
ALTER TABLE webauthn_credentials ADD COLUMN name VARCHAR(255);
