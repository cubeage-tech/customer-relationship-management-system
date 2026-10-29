package com.company.crm.meeting.service;

import com.company.crm.ai.service.MeetingSummaryService;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.storage.DocumentStorage;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FR-AI-7.1: recording a customer meeting — only with consent. Every recording carries who agreed to
 * be recorded, how (verbal, written or email) and when; without that confirmation it is refused
 * (400 RECORDING_CONSENT_REQUIRED). Audio only (WebM/Ogg as browsers record it, MP3, M4A, WAV),
 * checked by its leading bytes, up to the upload limit. Who may see, play or delete a recording
 * follows the meeting (its customer's or lead's data scope); every play and delete is audited, and
 * deleting it (e.g. consent withdrawn) removes the file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingRecordingService {

    public static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Set<String> METHODS = Set.of("verbal", "written", "email");
    /** Recording may start a little before the meeting's start time. */
    private static final int EARLY_MINUTES = 15;

    public record RecordingReq(boolean consentConfirmed, String consentGivenBy, String consentMethod, String consentNote,
                               Integer durationSeconds) {}

    public record RecordingRes(Long id, Long meetingId, String fileName, String contentType, long sizeBytes, Integer durationSeconds,
                               String consentGivenBy, String consentMethod, LocalDateTime consentAt, String consentNote,
                               String recordedByName, LocalDateTime recordedAt) {}

    public record Audio(String fileName, String contentType, long sizeBytes, InputStream content) {}

    private record Kind(String contentType, String extension) {}

    private final MeetingService meetingService;
    private final DocumentStorage storage;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RecordingRes> list(User user, Long meetingId) {
        Meeting meeting = meetingService.requireAccessible(user, meetingId);
        return jdbc.query(SELECT + " WHERE r.tenant_id = ? AND r.meeting_id = ? ORDER BY r.created_at DESC", this::map,
                meeting.getTenant().getId(), meeting.getId());
    }

    @Transactional
    public RecordingRes upload(User user, Long meetingId, MultipartFile file, RecordingReq req) {
        Meeting meeting = meetingService.requireAccessible(user, meetingId);
        String givenBy = req.consentGivenBy() == null ? "" : req.consentGivenBy().trim();
        if (!req.consentConfirmed() || givenBy.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Record who agreed to the recording and confirm their consent first",
                    "RECORDING_CONSENT_REQUIRED");
        }
        if (givenBy.length() > 200 || (req.consentNote() != null && req.consentNote().length() > 500)) {
            throw ApiException.badRequest("Consent details are too long");
        }
        if (!METHODS.contains(req.consentMethod())) {
            throw ApiException.badRequest("Consent method must be verbal, written or email");
        }
        if (meeting.isCancelled()) {
            throw ApiException.badRequest("A cancelled meeting can't be recorded");
        }
        if (meeting.getStartsAt().isAfter(LocalDateTime.now().plusMinutes(EARLY_MINUTES))) {
            throw ApiException.badRequest("Recording opens " + EARLY_MINUTES + " minutes before the meeting starts");
        }
        if (req.durationSeconds() != null && req.durationSeconds() < 0) {
            throw ApiException.badRequest("Duration can't be negative");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose or record the audio to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Recording is too large — the limit is 25 MB (about 1½ hours of browser audio)");
        }
        Kind kind = detect(file);
        Long tenantId = meeting.getTenant().getId();
        String key = tenantId + "/meeting/" + meeting.getId() + "/" + UUID.randomUUID();
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in);
        } catch (IOException e) {
            log.error("Could not store a recording for meeting {}: {}", meeting.getId(), e.getMessage());
            throw new UncheckedIOException("Could not store the recording", e);
        }
        afterCompletion(false, key); // a rolled-back row leaves no orphan file
        String fileName = "meeting-" + meeting.getId() + "-" + LocalDateTime.now().toLocalDate() + "." + kind.extension();
        Long id = jdbc.queryForObject("INSERT INTO meeting_recordings (tenant_id, meeting_id, storage_provider, storage_key, file_name, content_type, "
                        + "size_bytes, duration_seconds, consent_given_by, consent_method, consent_at, consent_note, recorded_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                tenantId, meeting.getId(), storage.provider(), key, fileName, kind.contentType(), file.getSize(), req.durationSeconds(),
                givenBy, req.consentMethod(), Timestamp.valueOf(LocalDateTime.now()),
                req.consentNote() == null || req.consentNote().isBlank() ? null : req.consentNote().trim(), user.getId());
        auditService.record(user, AuditAction.MEETING_RECORDING_ADDED, "meeting", meeting.getId(),
                "recording #" + id + ", consent " + req.consentMethod() + " by " + givenBy);
        return find(tenantId, meeting.getId(), id);
    }

    @Transactional
    public Audio open(User user, Long meetingId, Long recordingId) {
        Meeting meeting = meetingService.requireAccessible(user, meetingId);
        Map<String, Object> row = row(meeting, recordingId);
        auditService.recordAccess(user, AuditAction.MEETING_RECORDING_PLAYED, "meeting", meeting.getId(), "recording #" + recordingId);
        try {
            return new Audio((String) row.get("file_name"), (String) row.get("content_type"), ((Number) row.get("size_bytes")).longValue(),
                    storage.open((String) row.get("storage_key")));
        } catch (IOException e) {
            log.error("Stored audio missing for recording {}: {}", recordingId, e.getMessage());
            throw ApiException.notFound("The audio for this recording is no longer available");
        }
    }

    /** E.g. when a participant withdraws consent. */
    @Transactional
    public void delete(User user, Long meetingId, Long recordingId, String reason) {
        Meeting meeting = meetingService.requireAccessible(user, meetingId);
        Map<String, Object> row = row(meeting, recordingId);
        // FR-AI-7.2: consent withdrawn — the transcript captured with it, and its summary, go too.
        MeetingSummaryService.clear(jdbc, "transcript_recording_id = ?", recordingId);
        jdbc.update("DELETE FROM meeting_recordings WHERE id = ?", recordingId);
        afterCompletion(true, (String) row.get("storage_key")); // remove the audio only once the row is really gone
        auditService.record(user, AuditAction.MEETING_RECORDING_DELETED, "meeting", meeting.getId(),
                "recording #" + recordingId + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()));
    }

    // ==================== helpers ====================

    private static final String SELECT = "SELECT r.id, r.meeting_id, r.file_name, r.content_type, r.size_bytes, r.duration_seconds, r.consent_given_by, "
            + "r.consent_method, r.consent_at, r.consent_note, u.full_name, r.created_at FROM meeting_recordings r LEFT JOIN users u ON u.id = r.recorded_by";

    private RecordingRes find(Long tenantId, Long meetingId, Long id) {
        return jdbc.query(SELECT + " WHERE r.tenant_id = ? AND r.meeting_id = ? AND r.id = ?", this::map, tenantId, meetingId, id).get(0);
    }

    private Map<String, Object> row(Meeting meeting, Long recordingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT file_name, content_type, size_bytes, storage_key FROM meeting_recordings "
                + "WHERE id = ? AND tenant_id = ? AND meeting_id = ?", recordingId, meeting.getTenant().getId(), meeting.getId());
        if (rows.isEmpty()) {
            throw ApiException.notFound("Recording not found");
        }
        return rows.get(0);
    }

    private RecordingRes map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new RecordingRes(rs.getLong("id"), rs.getLong("meeting_id"), rs.getString("file_name"), rs.getString("content_type"),
                rs.getLong("size_bytes"), (Integer) rs.getObject("duration_seconds"), rs.getString("consent_given_by"),
                rs.getString("consent_method"), rs.getTimestamp("consent_at").toLocalDateTime(), rs.getString("consent_note"),
                rs.getString("full_name"), rs.getTimestamp("created_at").toLocalDateTime());
    }

    /** The audio format from the file's leading bytes; anything else is refused. */
    static Kind detect(MultipartFile file) {
        byte[] h;
        try (InputStream in = file.getInputStream()) {
            h = in.readNBytes(12);
        } catch (IOException e) {
            throw ApiException.badRequest("The recording could not be read");
        }
        if (starts(h, 0, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})) {
            return new Kind("audio/webm", "webm");
        }
        if (starts(h, 0, "OggS".getBytes(StandardCharsets.US_ASCII))) {
            return new Kind("audio/ogg", "ogg");
        }
        if (starts(h, 0, "ID3".getBytes(StandardCharsets.US_ASCII)) || (h.length > 1 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0)) {
            return new Kind("audio/mpeg", "mp3");
        }
        if (starts(h, 0, "RIFF".getBytes(StandardCharsets.US_ASCII)) && starts(h, 8, "WAVE".getBytes(StandardCharsets.US_ASCII))) {
            return new Kind("audio/wav", "wav");
        }
        if (starts(h, 4, "ftyp".getBytes(StandardCharsets.US_ASCII))) {
            return new Kind("audio/mp4", "m4a");
        }
        throw ApiException.badRequest("Only audio recordings (WebM, Ogg, MP3, M4A or WAV) can be uploaded");
    }

    private static boolean starts(byte[] head, int offset, byte[] signature) {
        return head.length >= offset + signature.length && Arrays.equals(Arrays.copyOfRange(head, offset, offset + signature.length), signature);
    }

    /** onCommit = true: delete the file after commit; false: delete it if the transaction rolls back. */
    private void afterCompletion(boolean onCommit, String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (onCommit) {
                deleteQuietly(key);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (onCommit ? status == STATUS_COMMITTED : status == STATUS_ROLLED_BACK) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete stored recording {}: {}", key, e.getMessage());
        }
    }
}
