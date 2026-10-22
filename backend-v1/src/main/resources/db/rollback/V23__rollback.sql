-- Manual rollback for V23__add_customer_documents.sql. Drops document metadata; delete the
-- files under DOCUMENT_STORAGE_PATH separately if they should not be kept.

BEGIN;
DROP TABLE IF EXISTS customer_documents;
DELETE FROM flyway_schema_history WHERE version = '23';
COMMIT;
