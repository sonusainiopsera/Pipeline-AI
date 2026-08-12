-- V5 migration: adds MFA setup lifecycle columns to the users table.
--
-- New columns:
--   users.mfa_setup_expires_at — timestamp after which an in-progress MFA enrollment
--     is considered expired and must be restarted (set to now()+10min on /mfa/setup,
--     cleared to NULL after /mfa/verify succeeds or on expiry cleanup).
--   users.recovery_codes — JSON array of BCrypt-hashed backup codes shown once at
--     setup time; stored as TEXT so the application layer controls serialization.
--
-- NEVER modify this file after it has been applied to any environment.
-- Use a new V6__ migration for any further schema changes.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mfa_setup_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS recovery_codes       TEXT;
