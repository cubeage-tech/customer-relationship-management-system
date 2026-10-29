-- FR-AI-7.2: the conversation of a meeting (its transcript) and the AI's summary of it. The
-- transcript comes from the browser's live captions while recording, or is pasted/uploaded (e.g. a
-- Zoom/Teams/Meet transcript) — with the same consent as a recording. Both are stored encrypted
-- (app.security.data-encryption-key). ai_summary holds the summary as JSON.
ALTER TABLE meetings
    ADD COLUMN transcript                  TEXT,
    ADD COLUMN transcript_source           VARCHAR(20) CHECK (transcript_source IN ('browser', 'upload', 'pasted')),
    ADD COLUMN transcript_recording_id     BIGINT REFERENCES meeting_recordings(id) ON DELETE SET NULL,
    ADD COLUMN transcript_consent_given_by VARCHAR(200),
    ADD COLUMN transcript_consent_method   VARCHAR(20) CHECK (transcript_consent_method IN ('verbal', 'written', 'email')),
    ADD COLUMN transcript_at               TIMESTAMP,
    ADD COLUMN transcript_by               BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN ai_summary                  TEXT,
    ADD COLUMN ai_summary_at               TIMESTAMP,
    ADD COLUMN ai_summary_by               BIGINT REFERENCES users(id) ON DELETE SET NULL;
