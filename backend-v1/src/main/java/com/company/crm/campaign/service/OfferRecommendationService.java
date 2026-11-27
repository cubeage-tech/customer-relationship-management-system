package com.company.crm.campaign.service;

import com.company.crm.campaign.entity.CustomerSegment;
import com.company.crm.campaign.entity.Promotion;
import com.company.crm.campaign.repository.CustomerSegmentQuery;
import com.company.crm.campaign.repository.PromotionRepository;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FR-7.7: the tenant's current offers ranked for one customer. An offer counts more when it was made
 * for a segment the customer is in, when customers of the same industry have taken it, when its value
 * suits the customer (worth more to one who usually wins on discount), when the customer has used
 * offers before, and when they haven't bought for a while. Each recommendation says why. Offers the
 * customer already used, expired or used-up ones aren't suggested. Computed from CRM data — no
 * language model.
 */
@Service
@RequiredArgsConstructor
public class OfferRecommendationService {

    private static final int MAX_OFFERS = 5;
    private static final int LAPSED_DAYS = 90;
    private static final int ENDING_SOON_DAYS = 14;

    private final JdbcTemplate jdbc;
    private final CustomerService customerService;
    private final PromotionRepository promotionRepository;
    private final CustomerSegmentQuery segmentQuery;

    public record OfferRec(Long promotionId, String code, String name, String description, String discountLabel, LocalDate validUntil,
                           int score, List<String> reasons) {}

    /** {@code marketingOptOut}: the customer unsubscribed — offer it in person, don't send it as marketing. */
    public record OfferRecs(Long customerId, String customerName, boolean marketingOptOut, List<OfferRec> offers) {}

    /** What the customer's history says about them. */
    private record Profile(String industry, boolean marketingOptOut, BigDecimal averageOrder, double usualDiscount, int redemptions,
                           Long daysSinceLastPurchase) {}

    @Transactional(readOnly = true)
    public OfferRecs recommend(User user, Long customerId) {
        CustomerResDto customer = customerService.getCustomer(user, customerId); // scope check
        Long tenantId = user.getTenant().getId();
        Profile profile = profile(tenantId, customerId, customer.getIndustry());
        LocalDate today = LocalDate.now();

        Set<Long> used = new HashSet<>(jdbc.queryForList("SELECT promotion_id FROM promotion_redemptions WHERE customer_id = ?", Long.class, customerId));
        Map<Long, long[]> uptake = new HashMap<>(); // promotion → [same-industry redemptions, all redemptions]
        jdbc.query("SELECT r.promotion_id, count(*) FILTER (WHERE c.industry = ?) AS same, count(*) AS total FROM promotion_redemptions r "
                + "JOIN customers c ON c.id = r.customer_id WHERE r.tenant_id = ? GROUP BY r.promotion_id", rs -> {
            uptake.put(rs.getLong("promotion_id"), new long[]{rs.getLong("same"), rs.getLong("total")});
        }, customer.getIndustry(), tenantId);
        long sameIndustryCustomers = jdbc.queryForObject("SELECT count(*) FROM customers WHERE tenant_id = ? AND industry = ? AND erased_at IS NULL",
                Long.class, tenantId, customer.getIndustry());
        long allCustomers = jdbc.queryForObject("SELECT count(*) FROM customers WHERE tenant_id = ? AND erased_at IS NULL", Long.class, tenantId);

        Map<Long, Boolean> memberOf = new HashMap<>();
        List<OfferRec> offers = new ArrayList<>();
        for (Promotion p : promotionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            long[] taken = uptake.getOrDefault(p.getId(), new long[2]);
            boolean current = p.isActive() && (p.getValidFrom() == null || !p.getValidFrom().isAfter(today))
                    && (p.getValidUntil() == null || !p.getValidUntil().isBefore(today));
            boolean usedUp = p.getMaxRedemptions() != null && taken[1] >= p.getMaxRedemptions();
            if (!current || usedUp || used.contains(p.getId())) {
                continue;
            }
            List<String> reasons = new ArrayList<>();

            // Made for a segment — is the customer in it?
            double segmentFit = 0.5;
            CustomerSegment segment = p.getCampaign() != null ? p.getCampaign().getSegment() : null;
            if (segment != null) {
                boolean member = memberOf.computeIfAbsent(segment.getId(),
                        id -> segmentQuery.customerIds(segment, LocalDateTime.now()).contains(customerId));
                segmentFit = member ? 1 : 0.1;
                reasons.add(member ? "Made for the segment \"" + segment.getName() + "\", which this customer is in"
                        : "Aimed at the segment \"" + segment.getName() + "\", which this customer isn't in");
            }

            // Taken by customers like this one (same industry), relative to that industry's share of customers.
            double industryShare = allCustomers == 0 ? 0 : (double) sameIndustryCustomers / allCustomers;
            double uptakeFit = taken[1] == 0 ? 0.3 : Math.min(1, ((taken[0] + 1.0) / (taken[1] + 2.0)) / Math.max(industryShare, 0.05) / 2);
            if (taken[0] > 0) {
                reasons.add("Taken by " + taken[0] + " other customer" + (taken[0] == 1 ? "" : "s") + " in the same industry");
            }

            // Worth it to this customer: the offer as a share of their typical order; more so if they usually win on discount.
            double effectivePercent = "percent".equals(p.getDiscountType()) ? p.getDiscountValue().doubleValue()
                    : profile.averageOrder() == null || profile.averageOrder().signum() == 0 ? 5
                    : p.getDiscountValue().doubleValue() * 100 / profile.averageOrder().doubleValue();
            double sensitivity = Math.min(1, 0.5 + profile.usualDiscount() / 20);
            double valueFit = Math.min(1, effectivePercent / 20) * sensitivity;
            if (profile.usualDiscount() >= 5) {
                reasons.add("Usually buys at about " + Math.round(profile.usualDiscount()) + "% off, so a discount matters to them");
            }

            double responsive = profile.redemptions() > 0 ? 1 : 0.3;
            if (profile.redemptions() > 0) {
                reasons.add("Has used " + profile.redemptions() + " offer" + (profile.redemptions() == 1 ? "" : "s") + " before");
            }
            double lapsed = profile.daysSinceLastPurchase() != null && profile.daysSinceLastPurchase() > LAPSED_DAYS ? 1 : 0.3;
            if (lapsed == 1) {
                reasons.add("No purchase in " + profile.daysSinceLastPurchase() + " days — an offer may bring them back");
            }
            if (p.getValidUntil() != null && ChronoUnit.DAYS.between(today, p.getValidUntil()) <= ENDING_SOON_DAYS) {
                reasons.add("Ends on " + p.getValidUntil());
            }

            int score = (int) Math.round(100 * (0.35 * segmentFit + 0.25 * uptakeFit + 0.2 * valueFit + 0.1 * responsive + 0.1 * lapsed));
            offers.add(new OfferRec(p.getId(), p.getCode(), p.getName(), p.getDescription(), label(p), p.getValidUntil(), score, reasons));
        }
        offers.sort(Comparator.comparingInt(OfferRec::score).reversed().thenComparing(OfferRec::name));
        return new OfferRecs(customerId, customer.getCompanyName(), profile.marketingOptOut(), offers.subList(0, Math.min(MAX_OFFERS, offers.size())));
    }

    private Profile profile(Long tenantId, Long customerId, String industry) {
        // Purchases: won deals and accepted quotations (net of line discounts).
        Map<String, Object> orders = jdbc.queryForMap("""
                SELECT avg(v) AS average, max(at) AS last FROM (
                    SELECT o.deal_value AS v, o.stage_changed_at AS at FROM opportunities o
                    WHERE o.tenant_id = ? AND o.customer_id = ? AND o.stage = 'won'
                    UNION ALL
                    SELECT sum(i.quantity * i.unit_price * (1 - i.discount_percent / 100)), q.customer_approved_at
                    FROM quotations q JOIN quotation_line_items i ON i.quotation_id = q.id
                    WHERE q.tenant_id = ? AND q.customer_id = ? AND q.status = 'approved' AND q.opportunity_id IS NULL
                    GROUP BY q.id, q.customer_approved_at) purchases""", tenantId, customerId, tenantId, customerId);
        Double usualDiscount = jdbc.queryForObject("SELECT avg(i.discount_percent) FROM quotation_line_items i JOIN quotations q ON q.id = i.quotation_id "
                + "WHERE q.tenant_id = ? AND q.customer_id = ? AND q.status = 'approved'", Double.class, tenantId, customerId);
        int redemptions = jdbc.queryForObject("SELECT count(*) FROM promotion_redemptions WHERE customer_id = ?", Integer.class, customerId);
        Boolean optOut = jdbc.queryForObject("SELECT marketing_opt_out FROM customers WHERE id = ?", Boolean.class, customerId);
        Object last = orders.get("last");
        Long days = last instanceof java.sql.Timestamp t ? ChronoUnit.DAYS.between(t.toLocalDateTime().toLocalDate(), LocalDate.now()) : null;
        BigDecimal average = orders.get("average") instanceof BigDecimal b ? b : null;
        return new Profile(industry, Boolean.TRUE.equals(optOut), average, usualDiscount == null ? 0 : usualDiscount, redemptions, days);
    }

    private static String label(Promotion p) {
        return "percent".equals(p.getDiscountType()) ? p.getDiscountValue().stripTrailingZeros().toPlainString() + "% off"
                : "₹" + p.getDiscountValue().stripTrailingZeros().toPlainString() + " off";
    }
}
