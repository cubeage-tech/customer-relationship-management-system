-- FR-AI-7.1: audio recordings of customer meetings, kept only with the participants' consent — who
-- agreed, how (verbal / written / email) and when are required with every recording. The audio
-- lives in document storage under a generated key; deleting the row (consent withdrawn, data
-- erasure) removes the file.
CREATE TABLE meeting_recordings (
    id                BIGSERIAL    PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL REFERENCES tenants(id),
    meeting_id        BIGINT       NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    storage_provider  VARCHAR(20)  NOT NULL DEFAULT 'local',
    storage_key       VARCHAR(255) NOT NULL UNIQUE,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(50)  NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    duration_seconds  INTEGER      CHECK (duration_seconds >= 0),
    consent_given_by  VARCHAR(200) NOT NULL,
    consent_method    VARCHAR(20)  NOT NULL CHECK (consent_method IN ('verbal', 'written', 'email')),
    consent_at        TIMESTAMP    NOT NULL,
    consent_note      VARCHAR(500),
    recorded_by       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_meeting_recordings_meeting ON meeting_recordings (tenant_id, meeting_id, created_at DESC);
