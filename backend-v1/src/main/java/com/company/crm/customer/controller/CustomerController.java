package com.company.crm.customer.controller;

import com.company.crm.common.response.Response;
import com.company.crm.customer.dto.request.CustomerContactReqDto;
import com.company.crm.customer.dto.request.CustomerReqDto;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module 1 — Customer Management (Customer 360°).
 * Every endpoint is tenant-scoped in the service layer; super_admin (no tenant) has no access.
 * Roles mirror frontend/core/constants/permission.constant.js's CUSTOMERS_* entries:
 * view = every tenant role, create/archive = admin + sales_manager, edit = + sales_executive (own only).
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Response<List<CustomerResDto>> listCustomers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return Response.ok(customerService.listCustomers(currentUser, industry, status, search));
    }

    @GetMapping("/{customerId}")
    public Response<CustomerResDto> getCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok(customerService.getCustomer(currentUser, customerId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<CustomerResDto> createCustomer(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CustomerReqDto dto) {
        return Response.ok("Customer created", customerService.createCustomer(currentUser, dto));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> updateCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerReqDto dto) {
        return Response.ok("Customer updated", customerService.updateCustomer(currentUser, customerId, dto));
    }

    @PatchMapping("/{customerId}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<CustomerResDto> archiveCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok("Customer archived", customerService.archiveCustomer(currentUser, customerId));
    }

    @PatchMapping("/{customerId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<CustomerResDto> restoreCustomer(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId) {
        return Response.ok("Customer restored", customerService.restoreCustomer(currentUser, customerId));
    }

    @PostMapping("/{customerId}/contacts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> addContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerContactReqDto dto) {
        return Response.ok("Contact added", customerService.addContact(currentUser, customerId, dto));
    }

    @PutMapping("/{customerId}/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> updateContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long contactId,
            @Valid @RequestBody CustomerContactReqDto dto) {
        return Response.ok("Contact updated", customerService.updateContact(currentUser, customerId, contactId, dto));
    }

    @DeleteMapping("/{customerId}/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<CustomerResDto> deleteContact(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long customerId,
            @PathVariable Long contactId) {
        return Response.ok("Contact removed", customerService.deleteContact(currentUser, customerId, contactId));
    }
}
