package com.company.crm.campaign.controller;

import com.company.crm.ai.perf.AiLatency;
import com.company.crm.campaign.service.OfferRecommendationService;
import com.company.crm.campaign.dto.request.PromotionReqDto;
import com.company.crm.campaign.dto.request.RedemptionReqDto;
import com.company.crm.campaign.dto.response.PromotionResDto;
import com.company.crm.campaign.service.PromotionService;
import com.company.crm.common.response.Response;
import com.company.crm.common.security.RoleExpressions;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FR-7.3: promotions/offers. Marketing (and the admin) manage them; sales staff — who apply offers
 * on deals — can see them and record redemptions against customers.
 */
@RestController
@RequiredArgsConstructor
public class PromotionController {

    private static final String MANAGE_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE')";
    private static final String USE_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'SALES_MANAGER', 'SALES_EXECUTIVE', 'EXECUTIVE_OWNER')";
    private static final String REDEEM_ROLES = "hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'SALES_MANAGER', 'SALES_EXECUTIVE')";

    private final OfferRecommendationService offerRecommendations;
    private final AiLatency latency;
    private final PromotionService promotionService;

    @GetMapping("/api/promotions")
    @PreAuthorize(USE_ROLES)
    @Operation(summary = "FR-7.3: promotions with their redemption counts")
    public Response<List<PromotionResDto>> list(@AuthenticationPrincipal User currentUser) {
        return Response.ok(promotionService.list(currentUser));
    }

    @PostMapping("/api/promotions")
    @PreAuthorize(MANAGE_ROLES)
    @Operation(summary = "FR-7.3: create a promotion")
    public Response<PromotionResDto> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody PromotionReqDto dto) {
        return Response.ok("Promotion created", promotionService.create(currentUser, dto));
    }

    @PutMapping("/api/promotions/{promotionId}")
    @PreAuthorize(MANAGE_ROLES)
    @Operation(summary = "FR-7.3: edit a promotion")
    public Response<PromotionResDto> update(@AuthenticationPrincipal User currentUser, @PathVariable Long promotionId,
                                            @Valid @RequestBody PromotionReqDto dto) {
        return Response.ok("Promotion updated", promotionService.update(currentUser, promotionId, dto));
    }

    @GetMapping("/api/promotions/{promotionId}/redemptions")
    @PreAuthorize(USE_ROLES)
    @Operation(summary = "FR-7.3: who used this promotion")
    public Response<List<PromotionResDto.Redemption>> redemptions(@AuthenticationPrincipal User currentUser, @PathVariable Long promotionId) {
        return Response.ok(promotionService.redemptions(currentUser, promotionId));
    }

    @PostMapping("/api/promotions/{promotionId}/redemptions")
    @PreAuthorize(REDEEM_ROLES)
    @Operation(summary = "FR-7.3: record that a customer used this promotion (once per customer)")
    public Response<PromotionResDto.Redemption> redeem(@AuthenticationPrincipal User currentUser, @PathVariable Long promotionId,
                                                       @Valid @RequestBody RedemptionReqDto dto) {
        return Response.ok("Redemption recorded", promotionService.redeem(currentUser, promotionId, dto));
    }

    @GetMapping("/api/customers/{customerId}/offer-recommendations")
    @PreAuthorize(USE_ROLES)
    @Operation(summary = "FR-7.7: the current offers ranked for this customer, with the reasons")
    public Response<OfferRecommendationService.OfferRecs> offerRecommendations(@AuthenticationPrincipal User currentUser,
                                                                               @PathVariable Long customerId) {
        return Response.ok(latency.time("offer_recommendation", AiLatency.Kind.STATISTICAL, () -> offerRecommendations.recommend(currentUser, customerId)));
    }

    @GetMapping("/api/customers/{customerId}/promotions")
    @PreAuthorize(RoleExpressions.ANY_TENANT_ROLE)
    @Operation(summary = "FR-7.3: the promotions this customer has used")
    public Response<List<PromotionResDto.Redemption>> customerRedemptions(@AuthenticationPrincipal User currentUser, @PathVariable Long customerId) {
        return Response.ok(promotionService.customerRedemptions(currentUser, customerId));
    }
}
