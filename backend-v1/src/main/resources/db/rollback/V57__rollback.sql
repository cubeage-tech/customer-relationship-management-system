-- Manual rollback for V57__add_meeting_recordings.sql. The recording rows (and their consent
-- records) are lost; delete the audio files under <tenant>/meeting/ in document storage separately.

BEGIN;
DROP TABLE IF EXISTS meeting_recordings;
DELETE FROM flyway_schema_history WHERE version = '57';
COMMIT;
