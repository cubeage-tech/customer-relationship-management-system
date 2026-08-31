-- BR-3/FR-4.3: optional note recorded when a discount approval is rejected.
-- Split out as its own migration because V9 had already been applied by a local
-- dev run before this field was added — editing an applied migration breaks
-- Flyway's checksum validation, so this goes in as a follow-up ALTER instead.

ALTER TABLE quotations ADD COLUMN discount_review_note VARCHAR(500);
