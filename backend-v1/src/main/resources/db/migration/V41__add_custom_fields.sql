-- ARC-9: industry-specific configuration on the common core. A tenant defines extra fields for
-- customers, leads and opportunities, for every industry or for one industry only, and can start
-- from a built-in industry template. Values live in one JSON document per record.

CREATE TABLE custom_field_definitions (
    id           BIGSERIAL     PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES tenants(id),
    entity_type  VARCHAR(20)   NOT NULL CHECK (entity_type IN ('customer', 'lead', 'opportunity')),
    field_key    VARCHAR(50)   NOT NULL,
    label        VARCHAR(100)  NOT NULL,
    field_type   VARCHAR(20)   NOT NULL CHECK (field_type IN ('text', 'number', 'date', 'select', 'checkbox')),
    options      JSONB,
    required     BOOLEAN       NOT NULL DEFAULT false,
    -- industry code the field applies to; NULL = every industry
    industry     VARCHAR(50),
    position     INT           NOT NULL DEFAULT 0,
    active       BOOLEAN       NOT NULL DEFAULT true,
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, field_key)
);

CREATE TABLE custom_field_values (
    id           BIGSERIAL     PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES tenants(id),
    entity_type  VARCHAR(20)   NOT NULL,
    record_id    BIGINT        NOT NULL,
    field_values JSONB         NOT NULL DEFAULT '{}'::jsonb,
    updated_by   BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    updated_at   TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, record_id)
);
