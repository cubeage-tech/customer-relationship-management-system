-- FR-AI-9.1: pairs of records a user confirmed are NOT duplicates, so the duplicate scan stops
-- suggesting them. first_id < second_id; record_type allows contacts later (FR-AI-9.2).
CREATE TABLE duplicate_dismissals (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
    record_type   VARCHAR(20)  NOT NULL CHECK (record_type IN ('customer')),
    first_id      BIGINT       NOT NULL,
    second_id     BIGINT       NOT NULL,
    dismissed_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (first_id < second_id),
    CONSTRAINT uq_duplicate_dismissals UNIQUE (tenant_id, record_type, first_id, second_id)
);
