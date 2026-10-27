-- INT-8: the last known state of each outside service the CRM signs in to (SMS/WhatsApp, email,
-- push, AI, payments). A rejected credential is recorded once and the platform admins are told to
-- re-authorise; the next successful call clears it.

CREATE TABLE integration_health (
    integration  VARCHAR(30)   PRIMARY KEY,
    status       VARCHAR(20)   NOT NULL CHECK (status IN ('ok', 'auth_failed')),
    last_error   VARCHAR(300),
    failed_at    TIMESTAMP,
    last_ok_at   TIMESTAMP,
    notified_at  TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT now()
);
