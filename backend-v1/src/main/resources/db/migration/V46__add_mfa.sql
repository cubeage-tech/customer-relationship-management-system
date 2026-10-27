-- SEC-1c: two-step sign-in (TOTP authenticator app) — required for the higher-privilege roles,
-- optional for everyone else. The secret is encrypted at rest (like other personal data); recovery
-- codes and sign-in challenge tokens are stored only as SHA-256 hashes.

ALTER TABLE users ADD COLUMN mfa_secret VARCHAR(512);
ALTER TABLE users ADD COLUMN mfa_pending_secret VARCHAR(512);
-- last accepted 30-second time step, so a code can't be used twice
ALTER TABLE users ADD COLUMN mfa_last_step BIGINT;
ALTER TABLE users ADD COLUMN mfa_enabled_at TIMESTAMP;

CREATE TABLE mfa_recovery_codes (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash  VARCHAR(64)  NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_recovery_codes_user ON mfa_recovery_codes(user_id);

-- After the password step: proves who is signing in until the code is entered (or setup is done).
CREATE TABLE mfa_challenges (
    id          BIGSERIAL    PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose     VARCHAR(10)  NOT NULL CHECK (purpose IN ('verify', 'setup')),
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
