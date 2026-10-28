package com.company.crm.meeting.controller;

import com.company.crm.ai.perf.AiLatency;
import com.company.crm.ai.service.MeetingSummaryService;
import com.company.crm.ai.service.MeetingSummaryService.Conversation;
import com.company.crm.ai.service.MeetingSummaryService.Summary;
import com.company.crm.ai.service.MeetingSummaryService.TranscriptReq;
import com.company.crm.ai.service.MeetingSummaryService.ProposedTask;
import com.company.crm.common.response.Response;
import com.company.crm.task.service.TaskService;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-AI-7.2 / 7.3: a meeting's conversation (transcript, with consent), its AI summary, and its action items as tasks. */
@RestController
@RequestMapping("/api/meetings/{meetingId}")
@RequiredArgsConstructor
public class MeetingConversationController {

    /** Same roles that schedule, record and report on meetings. */
    private static final String MEETING_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";

    private final MeetingSummaryService summaries;
    private final AiLatency latency;
    private final TaskService tasks;

    @GetMapping("/conversation")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-7.2: the meeting's transcript (with its consent) and AI summary, if any")
    public Response<Conversation> conversation(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId) {
        return Response.ok(summaries.get(currentUser, meetingId));
    }

    @PutMapping("/transcript")
    @PreAuthorize(MEETING_ROLES)
    @Operation(summary = "FR-AI-7.2: save the transcript — captured with a recording (recordingId), or pasted/uploaded with consent")
    public Response<Conversation> saveTranscript(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                                 @RequestBody TranscriptReq req) {
        return Response.ok("Transcript saved", summaries.saveTranscript(currentUser, meetingId, req));
    }

    @DeleteMapping("/transcript")
    @PreAuthorize(MEETING_ROLES)
    @Operation(summary = "FR-AI-7.2: delete the transcript and its summary (audited)")
    public Response<Void> deleteTranscript(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                           @RequestParam(required = false) String reason) {
        summaries.deleteTranscript(currentUser, meetingId, reason);
        return Response.ok("Transcript deleted", null);
    }

    @PostMapping("/action-items")
    @PreAuthorize(MEETING_ROLES)
    @Operation(summary = "FR-AI-7.3: the action items in the meeting's conversation, as proposed tasks (nothing is created)")
    public Response<List<ProposedTask>> actionItems(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId) {
        return Response.ok(latency.time("meeting_action_items", AiLatency.Kind.GENERATIVE, () -> summaries.actionItems(currentUser, meetingId)));
    }

    /** The items the user kept (edited as they like). */
    public record MeetingTasksReq(List<TaskService.MeetingTask> items) {}

    @PostMapping("/tasks")
    @PreAuthorize(MEETING_ROLES)
    @Operation(summary = "FR-AI-7.3: create follow-up tasks from the confirmed action items, on the meeting's customer or lead")
    public Response<List<TaskService.TaskRes>> createTasks(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId,
                                                           @RequestBody MeetingTasksReq req) {
        return Response.ok("Tasks created", tasks.createFromMeeting(currentUser, meetingId, req.items()));
    }

    @PostMapping("/ai-summary")
    @PreAuthorize(MEETING_ROLES)
    @Operation(summary = "FR-AI-7.2: summarise the meeting from its transcript (overview, key points, decisions, concerns, next steps)")
    public Response<Summary> summarize(@AuthenticationPrincipal User currentUser, @PathVariable Long meetingId) {
        return Response.ok(latency.time("meeting_summary", AiLatency.Kind.GENERATIVE, () -> summaries.summarize(currentUser, meetingId)));
    }
}
