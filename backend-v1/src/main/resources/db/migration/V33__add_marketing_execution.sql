-- =============================================================================
-- Module 7 marketing execution. Additive only. Rollback: db/rollback/V33__rollback.sql.
--   FR-7.2  customer_segments: saved criteria (industry, purchase history,
--           engagement, lead source), evaluated live;
--   FR-7.1b campaigns.status 'scheduled' + scheduled_at (auto-launch), and
--           active campaigns complete after end_date;
--   FR-7.1c email/SMS campaigns send a message to a segment snapshot
--           (campaign_recipients), honouring customers.marketing_opt_out;
--   FR-7.4  per-recipient sent/opened/clicked (tracking_token);
--   FR-7.3  promotions and their redemptions against customers.
-- =============================================================================

CREATE TABLE customer_segments (
    id                        BIGSERIAL    PRIMARY KEY,
    tenant_id                 BIGINT       NOT NULL REFERENCES tenants(id),
    name                      VARCHAR(120) NOT NULL,
    description               VARCHAR(500),
    industries                VARCHAR(500),            -- comma-separated industry codes; NULL = any
    lead_source               VARCHAR(30),             -- came from a lead with this source; NULL = any
    purchased_within_days     INT CHECK (purchased_within_days > 0),
    not_purchased_within_days INT CHECK (not_purchased_within_days > 0),
    min_purchase_value        NUMERIC(14, 2) CHECK (min_purchase_value >= 0),
    contacted_within_days     INT CHECK (contacted_within_days > 0),
    not_contacted_within_days INT CHECK (not_contacted_within_days > 0),
    created_by                BIGINT       REFERENCES users(id),
    created_at                TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_customer_segments_tenant_name ON customer_segments(tenant_id, lower(name));

ALTER TABLE campaigns DROP CONSTRAINT IF EXISTS campaigns_status_check;
ALTER TABLE campaigns ADD CONSTRAINT campaigns_status_check
    CHECK (status IN ('draft', 'scheduled', 'active', 'paused', 'completed', 'cancelled'));
ALTER TABLE campaigns ADD COLUMN scheduled_at TIMESTAMP;
ALTER TABLE campaigns ADD COLUMN launched_at TIMESTAMP;
ALTER TABLE campaigns ADD COLUMN segment_id BIGINT REFERENCES customer_segments(id) ON DELETE SET NULL;
ALTER TABLE campaigns ADD COLUMN message_subject VARCHAR(200);
ALTER TABLE campaigns ADD COLUMN message_body TEXT;
CREATE INDEX idx_campaigns_scheduled ON campaigns(scheduled_at) WHERE status = 'scheduled';

ALTER TABLE customers ADD COLUMN marketing_opt_out BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE customers ADD COLUMN marketing_opt_out_at TIMESTAMP;

CREATE TABLE campaign_recipients (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenants(id),
    campaign_id    BIGINT       NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    customer_id    BIGINT       NOT NULL REFERENCES customers(id),
    address        VARCHAR(150),
    status         VARCHAR(20)  NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'sent', 'failed', 'skipped')),
    error_message  VARCHAR(500),
    tracking_token VARCHAR(64)  NOT NULL UNIQUE,
    sent_at        TIMESTAMP,
    opened_at      TIMESTAMP,
    clicked_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (campaign_id, customer_id)
);
CREATE INDEX idx_campaign_recipients_queue ON campaign_recipients(created_at) WHERE status = 'queued';
CREATE INDEX idx_campaign_recipients_campaign ON campaign_recipients(campaign_id, status);

CREATE TABLE promotions (
    id              BIGSERIAL     PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL REFERENCES tenants(id),
    campaign_id     BIGINT        REFERENCES campaigns(id) ON DELETE SET NULL,
    code            VARCHAR(40)   NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    description     VARCHAR(500),
    discount_type   VARCHAR(10)   NOT NULL CHECK (discount_type IN ('percent', 'amount')),
    discount_value  NUMERIC(14, 2) NOT NULL CHECK (discount_value > 0),
    valid_from      DATE,
    valid_until     DATE,
    max_redemptions INT           CHECK (max_redemptions > 0),
    active          BOOLEAN       NOT NULL DEFAULT true,
    created_by      BIGINT        REFERENCES users(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (discount_type <> 'percent' OR discount_value <= 100),
    CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);
CREATE UNIQUE INDEX uq_promotions_tenant_code ON promotions(tenant_id, upper(code));

CREATE TABLE promotion_redemptions (
    id              BIGSERIAL     PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL REFERENCES tenants(id),
    promotion_id    BIGINT        NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    customer_id     BIGINT        NOT NULL REFERENCES customers(id),
    quotation_id    BIGINT        REFERENCES quotations(id) ON DELETE SET NULL,
    discount_amount NUMERIC(14, 2) CHECK (discount_amount >= 0),
    notes           VARCHAR(500),
    redeemed_at     TIMESTAMP     NOT NULL DEFAULT now(),
    recorded_by     BIGINT        REFERENCES users(id),
    UNIQUE (promotion_id, customer_id)
);
CREATE INDEX idx_promotion_redemptions_customer ON promotion_redemptions(tenant_id, customer_id);
