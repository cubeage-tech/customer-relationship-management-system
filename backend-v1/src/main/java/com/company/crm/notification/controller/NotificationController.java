package com.company.crm.notification.controller;

import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.notification.dto.NotificationDtos.DeviceReq;
import com.company.crm.notification.dto.NotificationDtos.Inbox;
import com.company.crm.notification.dto.NotificationDtos.MessageReq;
import com.company.crm.notification.dto.NotificationDtos.MessageRes;
import com.company.crm.notification.service.NotificationService;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** FR-M.4: the notification inbox, manager messages, and the mobile devices pushes go to. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private static final String MANAGERS = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'EXECUTIVE_OWNER')";

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-M.4: my notifications, newest first, with the unread count")
    public Response<Inbox> inbox(@AuthenticationPrincipal User currentUser) {
        return Response.ok(notificationService.inbox(currentUser));
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-M.4: mark one notification read")
    public Response<Void> markRead(@AuthenticationPrincipal User currentUser, @PathVariable Long notificationId) {
        notificationService.markRead(currentUser, notificationId);
        return Response.ok("Marked read", null);
    }

    @PatchMapping("/notifications/read-all")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-M.4: mark all my notifications read")
    public Response<Void> markAllRead(@AuthenticationPrincipal User currentUser) {
        notificationService.markAllRead(currentUser);
        return Response.ok("All marked read", null);
    }

    @PostMapping("/notifications/messages")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "FR-M.4: message people you manage (userIds) or a team (teamId); pushed to their phones")
    public Response<MessageRes> sendMessage(@AuthenticationPrincipal User currentUser, @Valid @RequestBody MessageReq dto) {
        return Response.ok("Message sent", notificationService.sendMessage(currentUser, dto));
    }

    @PostMapping("/devices")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-M.4: register this phone's push token for the signed-in user")
    public Response<Void> registerDevice(@AuthenticationPrincipal User currentUser, @Valid @RequestBody DeviceReq dto) {
        notificationService.registerDevice(currentUser, dto);
        return Response.ok("Device registered", null);
    }

    @PostMapping("/devices/unregister")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-M.4: stop pushing to this phone (on sign-out)")
    public Response<Void> unregisterDevice(@AuthenticationPrincipal User currentUser, @Valid @RequestBody DeviceReq dto) {
        notificationService.unregisterDevice(currentUser, dto.getToken());
        return Response.ok("Device removed", null);
    }
}
