CREATE TABLE IF NOT EXISTS dynamic_skills (
    id VARCHAR(96) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dynamic_skills_enabled_created
    ON dynamic_skills (enabled, created_at DESC);
