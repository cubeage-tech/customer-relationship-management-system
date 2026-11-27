package com.company.crm.meeting.controller;

import com.company.crm.meeting.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * FR-5.5c: a user's subscribable meetings calendar. Calendar apps fetch it without logging in, so
 * it is public by design — the unguessable token in the path is the credential (see Security and
 * EndpointAuthorizationCoverageTest).
 */
@RestController
@RequestMapping("/api/public/calendar")
@RequiredArgsConstructor
public class PublicCalendarController {

    private final MeetingService meetingService;

    @GetMapping("/{token}")
    @Operation(summary = "FR-5.5c: iCalendar feed of the user's meetings")
    public ResponseEntity<String> feed(@PathVariable String token) {
        String clean = token.endsWith(".ics") ? token.substring(0, token.length() - 4) : token;
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(meetingService.feed(clean));
    }
}
