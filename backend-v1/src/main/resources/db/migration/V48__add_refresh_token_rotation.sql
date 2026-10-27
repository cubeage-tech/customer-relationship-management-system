-- API-G01: /auth/refresh. A sign-in now also returns a refresh token (only its SHA-256 is stored, in
-- the existing refresh_tokens.token column). Each refresh replaces the token with a new one of the
-- same family; presenting a replaced token again means it was stolen, so the whole family is revoked.

ALTER TABLE refresh_tokens ADD COLUMN family VARCHAR(36);
ALTER TABLE refresh_tokens ADD COLUMN revoked_reason VARCHAR(20);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family);
