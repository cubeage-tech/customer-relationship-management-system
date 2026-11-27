-- NOT-1: the weekly digest email. Each user can opt out; last_digest_at makes a run send at most
-- one digest per user per week, even when several instances run the job.

ALTER TABLE users ADD COLUMN weekly_digest BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN last_digest_at TIMESTAMP;
