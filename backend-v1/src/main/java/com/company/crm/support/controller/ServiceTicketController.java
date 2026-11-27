package com.company.crm.support.controller;

import com.company.crm.common.pagination.PageRequestFactory;
import com.company.crm.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import com.company.crm.support.dto.request.ServiceTicketAssignReqDto;
import com.company.crm.support.dto.request.ServiceTicketFeedbackReqDto;
import com.company.crm.support.dto.request.ServiceTicketReqDto;
import com.company.crm.support.dto.request.ServiceTicketStatusReqDto;
import com.company.crm.support.dto.response.ServiceTicketResDto;
import com.company.crm.support.dto.response.ServiceTicketSummaryDto;
import com.company.crm.support.service.ServiceTicketService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 6 — Service & Support Management.
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's TICKETS_* entries, which are
 * unusually fragmented compared to other modules — view, create, and edit are held by mostly
 * non-overlapping role sets:
 *   view    = admin + sales_manager + service_agent (assigned only) + executive_owner
 *   create  = admin + sales_executive + service_agent   (sales_manager can view but NOT create)
 *   edit/resolve/assign/feedback = admin + service_agent (assigned only)
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class ServiceTicketController {

    private final ServiceTicketService ticketService;

    private static final String VIEW_ROLES = "hasAnyRole('ADMIN', 'SALES_MANAGER', 'SERVICE_AGENT', 'EXECUTIVE_OWNER')";
    private static final String CREATE_ROLES = "hasAnyRole('ADMIN', 'SALES_EXECUTIVE', 'SERVICE_AGENT')";
    private static final String EDIT_ROLES = "hasAnyRole('ADMIN', 'SERVICE_AGENT')";

    /**
     * Without page/size: the original array response (unchanged for existing screens).
     * With page and/or size: a PageResponse, size capped by app.pagination.max-size.
     * {@code scope} (mine | queue) applies to service agents only; default is both.
     */
    @GetMapping
    @PreAuthorize(VIEW_ROLES)
    @Operation(summary = "List tickets; service agents see their own plus the unassigned queue",
            description = "scope=mine|queue narrows an agent's list. Send page/size to get a paged response; "
                    + "sort=createdAt|slaDueAt|priority|status[,asc|desc].")
    public Response<?> listTickets(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        if (PageRequestFactory.isPaged(page, size)) {
            return Response.ok(ticketService.listTicketsPage(currentUser, scope, status, priority, customerId, search, page, size, sort));
        }
        return Response.ok(ticketService.listTickets(currentUser, scope, status, priority, customerId, search));
    }

    @PostMapping("/{ticketId}/claim")
    @PreAuthorize("hasRole('SERVICE_AGENT')")
    @Operation(summary = "Claim an unassigned ticket (service agent)",
            description = "Atomic: if two agents claim at once, one wins and the other gets 409 Conflict.")
    public Response<ServiceTicketResDto> claimTicket(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId) {
        return Response.ok("Ticket claimed", ticketService.claimTicket(currentUser, ticketId));
    }

    @GetMapping("/summary")
    @PreAuthorize(VIEW_ROLES)
    public Response<ServiceTicketSummaryDto> getSummary(@AuthenticationPrincipal User currentUser) {
        return Response.ok(ticketService.getSummary(currentUser));
    }

    @GetMapping("/{ticketId}")
    @PreAuthorize(VIEW_ROLES)
    public Response<ServiceTicketResDto> getTicket(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId) {
        return Response.ok(ticketService.getTicket(currentUser, ticketId));
    }

    @PostMapping
    @PreAuthorize(CREATE_ROLES)
    public Response<ServiceTicketResDto> createTicket(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ServiceTicketReqDto dto) {
        return Response.ok("Service ticket created", ticketService.createTicket(currentUser, dto));
    }

    @PutMapping("/{ticketId}")
    @PreAuthorize(EDIT_ROLES)
    public Response<ServiceTicketResDto> updateTicket(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody ServiceTicketReqDto dto) {
        return Response.ok("Service ticket updated", ticketService.updateTicket(currentUser, ticketId, dto));
    }

    @PatchMapping("/{ticketId}/assign")
    @PreAuthorize(EDIT_ROLES)
    public Response<ServiceTicketResDto> assignTechnician(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody ServiceTicketAssignReqDto dto) {
        return Response.ok("Technician assigned", ticketService.assignTechnician(currentUser, ticketId, dto.getTechnicianId()));
    }

    @PatchMapping("/{ticketId}/status")
    @PreAuthorize(EDIT_ROLES)
    public Response<ServiceTicketResDto> changeStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody ServiceTicketStatusReqDto dto) {
        return Response.ok("Service ticket status updated", ticketService.changeStatus(currentUser, ticketId, dto));
    }

    @PatchMapping("/{ticketId}/feedback")
    @PreAuthorize(EDIT_ROLES)
    public Response<ServiceTicketResDto> recordFeedback(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody ServiceTicketFeedbackReqDto dto) {
        return Response.ok("Feedback recorded", ticketService.recordFeedback(currentUser, ticketId, dto));
    }
}
