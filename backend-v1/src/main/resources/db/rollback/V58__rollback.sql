-- Manual rollback for V58__add_meeting_transcript_and_summary.sql. Meeting transcripts and their
-- AI summaries are lost; recordings and meetings stay.

BEGIN;
ALTER TABLE meetings
    DROP COLUMN IF EXISTS transcript,
    DROP COLUMN IF EXISTS transcript_source,
    DROP COLUMN IF EXISTS transcript_recording_id,
    DROP COLUMN IF EXISTS transcript_consent_given_by,
    DROP COLUMN IF EXISTS transcript_consent_method,
    DROP COLUMN IF EXISTS transcript_at,
    DROP COLUMN IF EXISTS transcript_by,
    DROP COLUMN IF EXISTS ai_summary,
    DROP COLUMN IF EXISTS ai_summary_at,
    DROP COLUMN IF EXISTS ai_summary_by;
DELETE FROM flyway_schema_history WHERE version = '58';
COMMIT;
