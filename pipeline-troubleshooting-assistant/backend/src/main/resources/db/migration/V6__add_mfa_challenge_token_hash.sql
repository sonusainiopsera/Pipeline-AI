ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mfa_challenge_token_hash VARCHAR(64);
