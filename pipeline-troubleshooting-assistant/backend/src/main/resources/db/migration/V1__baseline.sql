-- V1 baseline migration: captures the schema derived from JPA entity annotations
-- using Hibernate's SpringPhysicalNamingStrategy (camelCase → snake_case).
--
-- Tables:
--   error_knowledge_base — 7 columns: id, error_pattern, category, root_cause,
--                           solution, severity, created_at
--   analyzed_logs        — 9 columns: id, log_text, category, root_cause,
--                           suggested_fix, customer_update, severity, confidence, created_at
--
-- Column types match Hibernate 6 default mappings:
--   @Id @GeneratedValue(IDENTITY) Long  → BIGSERIAL PRIMARY KEY
--   @Column(columnDefinition="TEXT")    → TEXT
--   String (no @Column)                 → VARCHAR(255)
--   Integer                             → INTEGER
--   LocalDateTime                       → TIMESTAMP
--
-- NEVER modify this file after it has been applied to any environment.
-- Use a new V2__ migration for any schema changes.

CREATE TABLE IF NOT EXISTS error_knowledge_base (
    id            BIGSERIAL    PRIMARY KEY,
    error_pattern TEXT,
    category      VARCHAR(255),
    root_cause    TEXT,
    solution      TEXT,
    severity      VARCHAR(255),
    created_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analyzed_logs (
    id              BIGSERIAL    PRIMARY KEY,
    log_text        TEXT,
    category        VARCHAR(255),
    root_cause      TEXT,
    suggested_fix   TEXT,
    customer_update TEXT,
    severity        VARCHAR(255),
    confidence      INTEGER,
    created_at      TIMESTAMP
);
