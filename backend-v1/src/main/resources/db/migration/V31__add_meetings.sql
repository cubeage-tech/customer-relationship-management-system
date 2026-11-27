-- =============================================================================
-- FR-5.5: meetings with a customer or lead.
--   a) scheduled/cancelled meetings with time, place, notes and organizer;
--   b) optional reminder N minutes before (organizer, plus the customer when
--      include_customer); reminder_sent_at makes it fire once per start time;
--   c) iCalendar: ical_uid/ical_sequence keep calendar apps in sync across
--      updates, and users.calendar_feed_token is the secret of each user's
--      subscribable feed. Additive only. Rollback: db/rollback/V31__rollback.sql.
-- =============================================================================

CREATE TABLE meetings (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants(id),
    customer_id      BIGINT       REFERENCES customers(id),
    lead_id          BIGINT       REFERENCES leads(id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    starts_at        TIMESTAMP    NOT NULL,
    ends_at          TIMESTAMP    NOT NULL,
    location         VARCHAR(300),
    notes            TEXT,
    organizer_id     BIGINT       NOT NULL REFERENCES users(id),
    status           VARCHAR(20)  NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'cancelled')),
    reminder_minutes INT          CHECK (reminder_minutes BETWEEN 1 AND 10080),
    include_customer BOOLEAN      NOT NULL DEFAULT false,
    reminder_sent_at TIMESTAMP,
    ical_uid         VARCHAR(100) NOT NULL UNIQUE,
    ical_sequence    INT          NOT NULL DEFAULT 0,
    created_by       BIGINT       REFERENCES users(id),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (customer_id IS NOT NULL OR lead_id IS NOT NULL),
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_meetings_tenant_customer ON meetings(tenant_id, customer_id, starts_at);
CREATE INDEX idx_meetings_tenant_lead ON meetings(tenant_id, lead_id, starts_at);
CREATE INDEX idx_meetings_organizer ON meetings(organizer_id, starts_at);
CREATE INDEX idx_meetings_reminder_due ON meetings(starts_at)
    WHERE status = 'scheduled' AND reminder_minutes IS NOT NULL AND reminder_sent_at IS NULL;

ALTER TABLE users ADD COLUMN calendar_feed_token VARCHAR(64);
CREATE UNIQUE INDEX uq_users_calendar_feed_token ON users(calendar_feed_token) WHERE calendar_feed_token IS NOT NULL;
