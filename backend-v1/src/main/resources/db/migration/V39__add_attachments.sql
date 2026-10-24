-- DB-E16 (SRS §9.2 "Document / Attachment — file metadata and cloud storage reference", Modules 1, 4, 6).
-- Customer documents (Module 1) already exist (V23). This adds files on quotations (Module 4) and
-- service tickets (Module 6), and records which storage backend holds each file so a cloud store
-- can be introduced alongside the local one.

ALTER TABLE customer_documents ADD COLUMN storage_provider VARCHAR(20) NOT NULL DEFAULT 'local';

CREATE TABLE attachments (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL REFERENCES tenants(id),
    entity_type       VARCHAR(20)   NOT NULL CHECK (entity_type IN ('quotation', 'service_ticket')),
    entity_id         BIGINT        NOT NULL,
    storage_provider  VARCHAR(20)   NOT NULL DEFAULT 'local',
    -- Generated path in the storage backend (tenant/type/id/uuid) — never derived from the file name.
    storage_key       VARCHAR(255)  NOT NULL UNIQUE,
    file_name         VARCHAR(255)  NOT NULL,
    content_type      VARCHAR(100)  NOT NULL,
    size_bytes        BIGINT        NOT NULL CHECK (size_bytes > 0),
    uploaded_by       BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_entity ON attachments(tenant_id, entity_type, entity_id, created_at DESC);
