package com.company.crm.campaign.controller;

import com.company.crm.campaign.dto.request.SegmentReqDto;
import com.company.crm.campaign.dto.response.SegmentResDto;
import com.company.crm.campaign.service.SegmentService;
import com.company.crm.common.response.Response;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-7.2: customer segments for marketing campaigns. */
@RestController
@RequestMapping("/api/segments")
@RequiredArgsConstructor
public class SegmentController {

    /** Same roles as campaigns. */
    private static final String VIEW_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'EXECUTIVE_OWNER')";
    private static final String MANAGE_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE')";

    private final SegmentService segmentService;

    @GetMapping
    @PreAuthorize(VIEW_ROLES)
    @Operation(summary = "FR-7.2: saved segments with their current customer count")
    public Response<List<SegmentResDto>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(segmentService.list(currentUser));
    }

    @GetMapping("/{segmentId}/customers")
    @PreAuthorize(VIEW_ROLES)
    @Operation(summary = "FR-7.2: the customers the segment matches now (first 200)")
    public Response<List<SegmentResDto.Member>> members(@AuthenticationPrincipal User currentUser, @PathVariable Long segmentId) {
        return Response.ok(segmentService.members(currentUser, segmentId));
    }

    @PostMapping
    @PreAuthorize(MANAGE_ROLES)
    @Operation(summary = "FR-7.2: save a segment")
    public Response<SegmentResDto> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody SegmentReqDto dto) {
        return Response.ok("Segment saved", segmentService.create(currentUser, dto));
    }

    @PutMapping("/{segmentId}")
    @PreAuthorize(MANAGE_ROLES)
    @Operation(summary = "FR-7.2: change a segment's name or criteria")
    public Response<SegmentResDto> update(@AuthenticationPrincipal User currentUser, @PathVariable Long segmentId,
                                          @Valid @RequestBody SegmentReqDto dto) {
        return Response.ok("Segment saved", segmentService.update(currentUser, segmentId, dto));
    }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize(MANAGE_ROLES)
    @Operation(summary = "FR-7.2: delete a segment")
    public Response<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable Long segmentId) {
        segmentService.delete(currentUser, segmentId);
        return Response.ok("Segment deleted", null);
    }
}
