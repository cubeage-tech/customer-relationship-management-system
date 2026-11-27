package com.company.crm.meeting.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.meeting.service.MeetingRecordingService;
import com.company.crm.meeting.service.MeetingRecordingService.RecordingReq;
import com.company.crm.meeting.service.MeetingRecordingService.RecordingRes;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** FR-AI-7.1: a meeting's audio recordings, each with the participants' consent. */
@RestController
@RequestMapping("/api/meetings/{meetingId}/recordings")
@RequiredArgsConstructor
public class MeetingRecordingController {

    /** Same roles that schedule and report on meetings. */
    private static final String RECORD_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";

    private final MeetingRecordingService recordings;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-7.1: the meeting's recordings, with who consented, how and when")
    public Response<List<RecordingRes>> list(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId) {
        return Response.ok(recordings.list(currentUser, meetingId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(RECORD_ROLES)
    @Operation(summary = "FR-AI-7.1: add a recording — requires consentConfirmed=true, consentGivenBy and consentMethod (verbal|written|email)")
    public Response<RecordingRes> upload(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "false") boolean consentConfirmed,
                                         @RequestParam(required = false) String consentGivenBy,
                                         @RequestParam(required = false) String consentMethod,
                                         @RequestParam(required = false) String consentNote,
                                         @RequestParam(required = false) Integer durationSeconds) {
        return Response.ok("Recording saved", recordings.upload(currentUser, meetingId, file,
                new RecordingReq(consentConfirmed, consentGivenBy, consentMethod, consentNote, durationSeconds)));
    }

    @GetMapping("/{recordingId}/audio")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-7.1: play a recording (audited)")
    public ResponseEntity<InputStreamResource> audio(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                                     @PathVariable Long recordingId) {
        MeetingRecordingService.Audio audio = recordings.open(currentUser, meetingId, recordingId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.contentType()))
                .contentLength(audio.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(audio.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(audio.content()));
    }

    @DeleteMapping("/{recordingId}")
    @PreAuthorize(RECORD_ROLES)
    @Operation(summary = "FR-AI-7.1: delete a recording, e.g. when consent is withdrawn (audited; the audio is removed)")
    public Response<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId, @PathVariable Long recordingId,
                                 @RequestParam(required = false) String reason) {
        recordings.delete(currentUser, meetingId, recordingId, reason);
        return Response.ok("Recording deleted", null);
    }
}
