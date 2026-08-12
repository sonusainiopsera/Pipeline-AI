-- V4 migration: adds a unique constraint on refresh_tokens.token_hash.
--
-- SHA-256 hex digests are already collision-resistant, but the unique constraint
-- provides defence-in-depth by making the column an implicit unique index that
-- allows O(1) lookup by hash and prevents duplicate token storage at the DB layer.
--
-- NEVER modify this file after it has been applied to any environment.
-- Use a new V5__ migration for any further schema changes.

ALTER TABLE refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash);
