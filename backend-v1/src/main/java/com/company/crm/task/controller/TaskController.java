package com.company.crm.task.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.task.service.TaskService;
import com.company.crm.task.service.TaskService.Assignee;
import com.company.crm.task.service.TaskService.TaskReq;
import com.company.crm.task.service.TaskService.TaskRes;
import com.company.crm.task.service.TaskService.TaskUpdate;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-AI-7.3: follow-up tasks on customers, leads and deals. */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    /** Those who work customers, leads and deals day to day. */
    private static final String TASK_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";

    private final TaskService tasks;

    @GetMapping
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-7.3: tasks — mine (default), or all I can see for a customer, lead or deal; status=open|done|cancelled")
    public Response<List<TaskRes>> list(@AuthenticationPrincipal User currentUser,
                                        @RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) Long leadId,
                                        @RequestParam(required = false) Long opportunityId,
                                        @RequestParam(required = false) String status) {
        boolean mine = customerId == null && leadId == null && opportunityId == null;
        return Response.ok(tasks.list(currentUser, mine, customerId, leadId, opportunityId, status));
    }

    @GetMapping("/assignees")
    @PreAuthorize(TASK_ROLES)
    @Operation(summary = "FR-AI-7.3: colleagues a task can be assigned to")
    public Response<List<Assignee>> assignees(@AuthenticationPrincipal User currentUser) {
        return Response.ok(tasks.assignees(currentUser));
    }

    @PostMapping
    @PreAuthorize(TASK_ROLES)
    @Operation(summary = "FR-AI-7.3: add a follow-up task to a customer, lead or deal")
    public Response<TaskRes> create(@AuthenticationPrincipal User currentUser, @RequestBody TaskReq req) {
        return Response.ok("Task added", tasks.create(currentUser, req));
    }

    @PatchMapping("/{taskId}")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-AI-7.3: change a task — mark it done, reopen, reschedule or reassign")
    public Response<TaskRes> update(@AuthenticationPrincipal User currentUser, @PathVariable Long taskId, @RequestBody TaskUpdate req) {
        return Response.ok(tasks.update(currentUser, taskId, req));
    }
}
