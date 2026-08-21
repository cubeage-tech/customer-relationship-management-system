package com.company.crm.plan.controller;

import com.company.crm.common.response.Response;
import com.company.crm.plan.dto.request.UpdatePlanPriceReqDto;
import com.company.crm.plan.dto.response.PlanPriceResDto;
import com.company.crm.plan.service.PlanPriceService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanPriceController {

    private final PlanPriceService planPriceService;

    /** Public — the marketing pricing page reads this without being logged in. */
    @GetMapping("/prices")
    public Response<List<PlanPriceResDto>> listPrices() {
        return Response.ok(planPriceService.listPrices());
    }

    @PutMapping("/{plan}/price")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Response<PlanPriceResDto> updatePrice(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String plan,
            @Valid @RequestBody UpdatePlanPriceReqDto dto) {
        return Response.ok("Plan price updated", planPriceService.updatePrice(currentUser, plan, dto));
    }
}
