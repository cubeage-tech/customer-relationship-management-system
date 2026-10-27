package com.company.crm.campaign.service;

import com.company.crm.campaign.dto.response.CampaignMetricsDto;
import com.company.crm.campaign.entity.Campaign;
import com.company.crm.campaign.entity.CustomerSegment;
import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.campaign.repository.CustomerSegmentQuery;
import com.company.crm.campaign.repository.CustomerSegmentRepository;
import com.company.crm.common.enums.CampaignChannel;
import com.company.crm.common.exception.ApiException;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-7.5 / FR-7.6: which segments are most likely to respond to a campaign, and what a campaign is
 * likely to achieve before it is launched. Learned from the tenant's own campaign history on the same
 * channel: each customer's past deliveries, opens, clicks and conversions (a won deal or accepted
 * quotation within 30 days), smoothed toward their industry's and the tenant's rates so a customer
 * with little history isn't over- or under-rated. With little history at all, typical rates are used
 * and the answer says so. Computed from CRM data — no language model.
 */
@Service
@RequiredArgsConstructor
public class CampaignInsightService {

    /** Sends of history it takes before the tenant's own rates outweigh the typical ones. */
    private static final double TYPICAL_WEIGHT = 30;
    private static final double INDUSTRY_WEIGHT = 20;
    private static final double CUSTOMER_WEIGHT = 4;
    /** ~80% range; widened for model uncertainty. */
    private static final double Z = 1.28;
    private static final double UNCERTAINTY = 1.5;
    private static final int CONVERSION_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final CampaignRepository campaignRepository;
    private final CustomerSegmentRepository segmentRepository;
    private final CustomerSegmentQuery segmentQuery;
    private final SegmentService segmentService;
    private final CampaignService campaignService;

    public record SegmentRec(Long segmentId, String name, long members, long reachable, double predictedResponseRate,
                             long expectedResponses, double lift, long membersWithHistory, String responseMeasure, String reason) {}

    public record Estimate(long low, long expected, long high) {}

    public record Actual(long sent, long opened, long clicked, long converted) {}

    public record CampaignPrediction(String channel, Long segmentId, String segmentName, long members, long reachable, long skipped,
                                     Estimate delivered, Estimate opened, Estimate clicked, Estimate conversions,
                                     Double deliveryRate, Double openRate, Double clickRate, Double conversionRate,
                                     BigDecimal budget, BigDecimal costPerConversion, String basis, long historySends,
                                     List<String> notes, Actual actual) {}

    /** Past outcomes on one channel: per customer, per industry and in total. */
    private static final class Counts {
        double attempted;
        double sent;
        double opened;
        double clicked;
        double converted;

        void add(Counts c) {
            attempted += c.attempted;
            sent += c.sent;
            opened += c.opened;
            clicked += c.clicked;
            converted += c.converted;
        }
    }

    /** What a customer is likely to do with one message: delivered, opened, clicked, converted. */
    private record Odds(double deliver, double open, double click, double convert) {}

    private record Customer(long id, String industry, boolean reachable) {}

    private record Model(CampaignChannel channel, Map<Long, Counts> byCustomer, Map<String, Counts> byIndustry, Counts total,
                         double deliverRate, double openRate, double clickRate, double convertRate) {

        boolean tracksOpens() {
            return channel == CampaignChannel.EMAIL;
        }

        Odds odds(Customer c) {
            Counts industry = byIndustry.get(c.industry());
            double deliverBase = blend(industry == null ? 0 : industry.sent, industry == null ? 0 : industry.attempted, deliverRate, INDUSTRY_WEIGHT);
            double openBase = industry == null ? openRate : blend(industry.opened, industry.sent, openRate, INDUSTRY_WEIGHT);
            double clickBase = industry == null ? clickRate : blend(industry.clicked, industry.sent, clickRate, INDUSTRY_WEIGHT);
            double convertBase = industry == null ? convertRate : blend(industry.converted, industry.sent, convertRate, INDUSTRY_WEIGHT);
            Counts own = byCustomer.get(c.id());
            if (own == null) {
                return new Odds(deliverBase, openBase, clickBase, convertBase);
            }
            return new Odds(blend(own.sent, own.attempted, deliverBase, CUSTOMER_WEIGHT), blend(own.opened, own.sent, openBase, CUSTOMER_WEIGHT),
                    blend(own.clicked, own.sent, clickBase, CUSTOMER_WEIGHT), blend(own.converted, own.sent, convertBase, CUSTOMER_WEIGHT));
        }

        /** The response the channel can measure: opens for email, conversions for SMS and WhatsApp. */
        double response(Odds o) {
            return tracksOpens() ? o.open() : o.convert();
        }
    }

    // ==================== FR-7.5 ====================

    @Transactional(readOnly = true)
    public List<SegmentRec> segmentRecommendations(User user, String channelCode) {
        Long tenantId = requireTenantId(user);
        CampaignChannel channel = sendableChannel(channelCode);
        Model model = model(tenantId, channel, null);
        Map<Long, Customer> customers = customers(tenantId, channel);
        double baseResponse = model.tracksOpens() ? model.openRate() : model.convertRate();
        String measure = model.tracksOpens() ? "open" : "conversion";

        List<SegmentRec> recs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (CustomerSegment segment : segmentRepository.findByTenantIdOrderByNameAsc(tenantId)) {
            List<Long> members = segmentQuery.customerIds(segment, now);
            double expected = 0;
            long reachable = 0;
            long withHistory = 0;
            for (Long id : members) {
                Customer c = customers.get(id);
                if (c == null || !c.reachable()) {
                    continue;
                }
                reachable++;
                Odds odds = model.odds(c);
                expected += odds.deliver() * model.response(odds);
                if (model.byCustomer().containsKey(id)) {
                    withHistory++;
                }
            }
            double rate = reachable == 0 ? 0 : expected / reachable;
            double lift = baseResponse == 0 ? 0 : rate / (model.deliverRate() * baseResponse);
            recs.add(new SegmentRec(segment.getId(), segment.getName(), members.size(), reachable, round3(rate), Math.round(expected),
                    round2(lift), withHistory, measure, reason(reachable, members.size(), withHistory, lift, measure)));
        }
        recs.sort(Comparator.comparingDouble(SegmentRec::predictedResponseRate).reversed()
                .thenComparing(Comparator.comparingLong(SegmentRec::expectedResponses).reversed()));
        return recs;
    }

    private static String reason(long reachable, long members, long withHistory, double lift, String measure) {
        if (reachable == 0) {
            return members == 0 ? "No customers match this segment today." : "No member can be reached on this channel (consent or address).";
        }
        String verb = "open".equals(measure) ? "open" : "convert";
        String comparison = lift >= 1.15 ? Math.round((lift - 1) * 100) + "% more likely to " + verb + " than your average"
                : lift <= 0.85 ? Math.round((1 - lift) * 100) + "% less likely to " + verb + " than your average"
                : "about as likely to " + verb + " as your average";
        String basis = withHistory * 2 >= reachable ? "based on how members responded before"
                : withHistory > 0 ? "partly estimated from their industry" : "estimated from their industry — no member has had this channel yet";
        return "Members are " + comparison + " (" + basis + ").";
    }

    // ==================== FR-7.6 ====================

    /** Before a campaign is saved: the channel, segment and budget being considered. */
    @Transactional(readOnly = true)
    public CampaignPrediction predict(User user, String channelCode, Long segmentId, BigDecimal budget) {
        Long tenantId = requireTenantId(user);
        CampaignChannel channel = sendableChannel(channelCode);
        if (segmentId == null) {
            throw ApiException.badRequest("Choose a segment to predict the campaign's performance");
        }
        CustomerSegment segment = segmentService.requireSegment(user, segmentId);
        List<Long> members = segmentQuery.customerIds(segment, LocalDateTime.now());
        return estimate(tenantId, channel, segment, members, null, budget, null);
    }

    /**
     * A saved campaign. Once launched it is predicted for the customers it actually went to, from the
     * history before it — next to what actually happened, so the prediction can be checked.
     */
    @Transactional(readOnly = true)
    public CampaignPrediction predict(User user, Long campaignId) {
        Long tenantId = requireTenantId(user);
        Campaign campaign = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Campaign not found"));
        CampaignChannel channel = sendableChannel(campaign.getChannel().getDbValue());
        if (campaign.getLaunchedAt() != null) {
            List<Long> recipients = jdbc.queryForList("SELECT customer_id FROM campaign_recipients WHERE campaign_id = ?", Long.class, campaignId);
            CampaignMetricsDto m = campaignService.metrics(user, campaignId);
            Actual actual = new Actual(m.sent(), m.opened(), m.clicked(), m.converted());
            return estimate(tenantId, channel, campaign.getSegment(), recipients, campaignId, campaign.getBudget(), actual);
        }
        if (campaign.getSegment() == null) {
            throw ApiException.badRequest("Choose a segment for this campaign to predict its performance");
        }
        return estimate(tenantId, channel, campaign.getSegment(), segmentQuery.customerIds(campaign.getSegment(), LocalDateTime.now()),
                campaignId, campaign.getBudget(), null);
    }

    private CampaignPrediction estimate(Long tenantId, CampaignChannel channel, CustomerSegment segment, List<Long> members,
                                        Long excludeCampaignId, BigDecimal budget, Actual actual) {
        Model model = model(tenantId, channel, excludeCampaignId);
        Map<Long, Customer> customers = customers(tenantId, channel);
        double[] mean = new double[4];
        double[] variance = new double[4];
        long reachable = 0;
        for (Long id : members) {
            Customer c = customers.get(id);
            if (c == null || !c.reachable()) {
                continue;
            }
            reachable++;
            Odds o = model.odds(c);
            double[] q = {o.deliver(), o.deliver() * o.open(), o.deliver() * o.click(), o.deliver() * o.convert()};
            for (int k = 0; k < 4; k++) {
                mean[k] += q[k];
                variance[k] += q[k] * (1 - q[k]);
            }
        }
        boolean opens = model.tracksOpens();
        Estimate conversions = range(mean[3], variance[3]);
        BigDecimal costPerConversion = budget == null || budget.signum() == 0 || conversions.expected() == 0 ? null
                : budget.divide(BigDecimal.valueOf(conversions.expected()), 2, RoundingMode.HALF_UP);

        List<String> notes = new ArrayList<>();
        boolean typical = model.total().sent < TYPICAL_WEIGHT;
        if (typical) {
            notes.add("Fewer than " + (int) TYPICAL_WEIGHT + " past " + channel.getDbValue()
                    + " messages, so typical industry rates fill in — the range is wide.");
        }
        if (!opens) {
            notes.add("SMS and WhatsApp have no open or click tracking, so deliveries and conversions are predicted.");
        }
        long skipped = members.size() - reachable;
        if (skipped > 0) {
            notes.add(skipped + " member" + (skipped == 1 ? "" : "s") + " can't be reached on this channel (unsubscribed, no consent or no address).");
        }
        return new CampaignPrediction(channel.getDbValue(), segment != null ? segment.getId() : null, segment != null ? segment.getName() : null,
                members.size(), reachable, skipped, range(mean[0], variance[0]), opens ? range(mean[1], variance[1]) : null,
                opens ? range(mean[2], variance[2]) : null, conversions,
                round3(model.deliverRate()), opens ? round3(model.openRate()) : null, opens ? round3(model.clickRate()) : null,
                round3(model.convertRate()), budget, costPerConversion, typical ? "typical" : "history", Math.round(model.total().sent),
                notes, actual);
    }

    // ==================== The model ====================

    /** Typical rates where the tenant has no history yet (per message sent). */
    private static double[] typical(CampaignChannel channel) {
        return switch (channel) {
            case EMAIL -> new double[]{0.97, 0.21, 0.025, 0.010};
            case WHATSAPP -> new double[]{0.95, 0, 0, 0.020};
            default -> new double[]{0.95, 0, 0, 0.008};
        };
    }

    private Model model(Long tenantId, CampaignChannel channel, Long excludeCampaignId) {
        Map<Long, Counts> byCustomer = new HashMap<>();
        Map<String, Counts> byIndustry = new HashMap<>();
        Counts total = new Counts();
        jdbc.query("""
                SELECT r.customer_id, cu.industry,
                       count(*) AS attempted,
                       count(*) FILTER (WHERE r.status = 'sent') AS sent,
                       count(r.opened_at) AS opened,
                       count(r.clicked_at) AS clicked,
                       sum(CASE WHEN r.status = 'sent' AND (
                           EXISTS (SELECT 1 FROM opportunities o WHERE o.customer_id = r.customer_id AND o.stage = 'won'
                                   AND o.stage_changed_at >= r.sent_at AND o.stage_changed_at < r.sent_at + make_interval(days => ?))
                        OR EXISTS (SELECT 1 FROM quotations q WHERE q.customer_id = r.customer_id AND q.status = 'approved'
                                   AND q.customer_approved_at >= r.sent_at AND q.customer_approved_at < r.sent_at + make_interval(days => ?)))
                           THEN 1 ELSE 0 END) AS converted
                FROM campaign_recipients r
                JOIN campaigns c ON c.id = r.campaign_id
                JOIN customers cu ON cu.id = r.customer_id
                WHERE r.tenant_id = ? AND c.channel = ? AND r.status IN ('sent', 'failed') AND r.campaign_id <> ?
                GROUP BY r.customer_id, cu.industry""", rs -> {
            Counts c = new Counts();
            c.attempted = rs.getLong("attempted");
            c.sent = rs.getLong("sent");
            c.opened = rs.getLong("opened");
            c.clicked = rs.getLong("clicked");
            c.converted = rs.getLong("converted");
            byCustomer.put(rs.getLong("customer_id"), c);
            byIndustry.computeIfAbsent(String.valueOf(rs.getString("industry")), k -> new Counts()).add(c);
            total.add(c);
        }, CONVERSION_DAYS, CONVERSION_DAYS, tenantId, channel.getDbValue(), excludeCampaignId == null ? -1L : excludeCampaignId);

        double[] t = typical(channel);
        return new Model(channel, byCustomer, byIndustry, total, blend(total.sent, total.attempted, t[0], TYPICAL_WEIGHT),
                blend(total.opened, total.sent, t[1], TYPICAL_WEIGHT), blend(total.clicked, total.sent, t[2], TYPICAL_WEIGHT),
                blend(total.converted, total.sent, t[3], TYPICAL_WEIGHT));
    }

    /** The tenant's customers, and whether each can be reached on the channel (as at launch: consent and an address). */
    private Map<Long, Customer> customers(Long tenantId, CampaignChannel channel) {
        String reachable = switch (channel) {
            case EMAIL -> "NOT c.marketing_opt_out AND (c.email_hash IS NOT NULL OR EXISTS (SELECT 1 FROM customer_contacts cc "
                    + "WHERE cc.customer_id = c.id AND cc.email_hash IS NOT NULL))";
            case WHATSAPP -> "NOT c.marketing_opt_out AND c.whatsapp_opt_in AND " + hasPhone();
            default -> "NOT c.marketing_opt_out AND NOT c.sms_opt_out AND " + hasPhone();
        };
        Map<Long, Customer> customers = new HashMap<>();
        jdbc.query("SELECT c.id, c.industry, (" + reachable + ") AS reachable FROM customers c WHERE c.tenant_id = ? AND c.erased_at IS NULL",
                rs -> {
                    long id = rs.getLong("id");
                    customers.put(id, new Customer(id, String.valueOf(rs.getString("industry")), rs.getBoolean("reachable")));
                }, tenantId);
        return customers;
    }

    private static String hasPhone() {
        return "(c.phone_hash IS NOT NULL OR EXISTS (SELECT 1 FROM customer_contacts cc WHERE cc.customer_id = c.id AND cc.phone_hash IS NOT NULL))";
    }

    /** {@code hits} out of {@code trials}, pulled toward {@code prior} as if it had {@code weight} trials of its own. */
    static double blend(double hits, double trials, double prior, double weight) {
        return (hits + weight * prior) / (trials + weight);
    }

    private static Estimate range(double mean, double variance) {
        double spread = Z * Math.sqrt(variance) * UNCERTAINTY;
        return new Estimate((long) Math.max(0, Math.floor(mean - spread)), Math.round(mean), (long) Math.ceil(mean + spread));
    }

    private static CampaignChannel sendableChannel(String code) {
        CampaignChannel channel;
        try {
            channel = CampaignChannel.fromDbValue(code == null ? "" : code.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown campaign channel: " + code);
        }
        if (!CampaignDeliveryService.isSendable(channel)) {
            throw ApiException.badRequest("Predictions are made for email, SMS and WhatsApp campaigns");
        }
        return channel;
    }

    private static Long requireTenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Campaigns are scoped to a tenant");
        }
        return user.getTenant().getId();
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
