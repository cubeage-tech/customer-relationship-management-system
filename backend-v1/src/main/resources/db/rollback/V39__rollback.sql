-- Manual rollback for V39__add_attachments.sql. Quotation and ticket attachment records are lost
-- (their stored files are left in place and can be removed from the storage folder).

BEGIN;
DROP TABLE IF EXISTS attachments;
ALTER TABLE customer_documents DROP COLUMN IF EXISTS storage_provider;
DELETE FROM flyway_schema_history WHERE version = '39';
COMMIT;
