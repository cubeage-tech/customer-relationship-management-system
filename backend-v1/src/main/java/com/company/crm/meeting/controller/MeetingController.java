package com.company.crm.meeting.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.meeting.dto.MeetingReportReqDto;
import com.company.crm.meeting.dto.MeetingReqDto;
import com.company.crm.meeting.dto.MeetingResDto;
import com.company.crm.meeting.service.MeetingService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** FR-5.5: meetings with customers and leads, their .ics files, and each user's calendar feed. */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    /** Same roles that log customer communications. */
    private static final String SCHEDULE_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";
    private static final MediaType CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final MeetingService meetingService;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-5.5a: a customer's (customerId) or lead's (leadId) meetings, or my upcoming meetings "
            + "(recent=true adds my last two weeks, FR-M.3)")
    public Response<List<MeetingResDto>> list(@AuthenticationPrincipal User currentUser,
                                              @RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) Long leadId,
                                              @RequestParam(defaultValue = "false") boolean recent) {
        return Response.ok(meetingService.list(currentUser, customerId, leadId, recent));
    }

    @PostMapping
    @PreAuthorize(SCHEDULE_ROLES)
    @Operation(summary = "FR-5.5a: schedule a meeting (invite emailed with an .ics)")
    public Response<MeetingResDto> schedule(@AuthenticationPrincipal User currentUser, @Valid @RequestBody MeetingReqDto dto) {
        return Response.ok("Meeting scheduled", meetingService.schedule(currentUser, dto));
    }

    @PutMapping("/{meetingId}")
    @PreAuthorize(SCHEDULE_ROLES)
    @Operation(summary = "FR-5.5a: reschedule or edit a meeting")
    public Response<MeetingResDto> update(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                          @Valid @RequestBody MeetingReqDto dto) {
        return Response.ok("Meeting updated", meetingService.update(currentUser, meetingId, dto));
    }

    @PutMapping("/{meetingId}/report")
    @PreAuthorize(SCHEDULE_ROLES)
    @Operation(summary = "FR-M.3: submit the meeting report — outcome, notes and next steps")
    public Response<MeetingResDto> report(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                          @Valid @RequestBody MeetingReportReqDto dto) {
        return Response.ok("Meeting report saved", meetingService.report(currentUser, meetingId, dto));
    }

    @PatchMapping("/{meetingId}/cancel")
    @PreAuthorize(SCHEDULE_ROLES)
    @Operation(summary = "FR-5.5a: cancel a meeting (a cancellation is sent to calendars)")
    public Response<MeetingResDto> cancel(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId) {
        return Response.ok("Meeting cancelled", meetingService.cancel(currentUser, meetingId));
    }

    @GetMapping("/{meetingId}/ics")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-5.5c: download the meeting as an .ics file")
    public ResponseEntity<String> ics(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId) {
        return ResponseEntity.ok()
                .contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("meeting-" + meetingId + ".ics").build().toString())
                .body(meetingService.ics(currentUser, meetingId));
    }

    @GetMapping("/calendar-feed")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-5.5c: my private calendar feed URL (subscribe to it in Google/Outlook/Apple Calendar)")
    public Response<Map<String, String>> feed(@AuthenticationPrincipal User currentUser) {
        return Response.ok(Map.of("url", feedUrl(meetingService.feedToken(currentUser))));
    }

    @PostMapping("/calendar-feed/reset")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-5.5c: replace my calendar feed URL; the old one stops working")
    public Response<Map<String, String>> resetFeed(@AuthenticationPrincipal User currentUser) {
        return Response.ok("Calendar link replaced", Map.of("url", feedUrl(meetingService.resetFeedToken(currentUser))));
    }

    /** Built from the address the browser used for the API, so the calendar app can reach it too. */
    private static String feedUrl(String token) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/public/calendar/{token}.ics")
                .buildAndExpand(token).toUriString();
    }
}
