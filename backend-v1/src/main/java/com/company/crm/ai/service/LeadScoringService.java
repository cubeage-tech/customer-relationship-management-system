package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.Factor;
import com.company.crm.ai.dto.AiDtos.LeadScore;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.event.LeadChangedEvent;
import com.company.crm.lead.service.LeadService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * FR-AI-1.1 / API-G11: a 0–100 lead score from the tenant's own history — how well leads of the same
 * industry and source have converted, the lead's budget against the tenant's typical won deal, the
 * company's buying behaviour (purchases it already made as a customer), how engaged the lead is
 * (messages, calls, meetings in the last 30 days), how recently it was contacted, and how far along
 * the pipeline it is. Every score comes with its factors, so a salesperson can see why.
 *
 * <p>FR-AI-1.2/1.3: the score and its High/Medium/Low label are kept on the lead — refreshed when the
 * lead changes and every 30 minutes for everything else (activity, other leads converting) — so the
 * lead list can show, filter and sort by it. Computed from CRM data; no language model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadScoringService {

    static final double W_INDUSTRY = 0.20;
    static final double W_SOURCE = 0.10;
    static final double W_BUDGET = 0.10;
    static final double W_BUYING = 0.10;
    static final double W_ENGAGEMENT = 0.20;
    static final double W_RECENCY = 0.15;
    static final double W_STAGE = 0.15;
    private static final List<String> STAGES = List.of("new_lead", "contacted", "meeting", "quotation", "negotiation", "converted");
    /** Ids per IN list, well under the database's limit on parameters per statement. */
    private static final int MAX_ID_LIST = 1000;

    private final LeadService leadService;
    private final JdbcTemplate jdbc;

    /** What scoring needs to know about a lead. */
    record Input(Long id, String leadName, String companyName, String industry, String source, String stage, LocalDateTime createdAt,
                 BigDecimal budget, Integer storedScore, String storedLabel) {

        static Input of(LeadResDto l) {
            return new Input(l.getId(), l.getLeadName(), l.getCompanyName(), l.getIndustry(), l.getSource(), l.getStage(), l.getCreatedAt(),
                    l.getBudget(), l.getAiScore(), l.getAiScoreLabel());
        }
    }

    /** One lead (the caller must be able to see it). */
    @Transactional(readOnly = true)
    public LeadScore score(User user, Long leadId) {
        LeadResDto lead = leadService.getLead(user, leadId);
        return scoreAll(user.getTenant().getId(), List.of(Input.of(lead))).get(0);
    }

    /** Every lead the caller can see, highest score first (FR-AI-1.3); optional minimum score. */
    @Transactional(readOnly = true)
    public List<LeadScore> scores(User user, Integer minScore) {
        List<Input> leads = leadService.listLeads(user, null, null, null, null, null).stream().map(Input::of).toList();
        return scoreAll(user.getTenant().getId(), leads).stream()
                .filter(s -> minScore == null || s.score() >= minScore)
                .sorted(Comparator.comparingInt(LeadScore::score).reversed())
                .toList();
    }

    // ==================== Stored scores (FR-AI-1.2/1.3) ====================

    /** After a lead is created or edited: its stored score, once the change has committed. */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLeadChanged(LeadChangedEvent event) {
        store(event.tenantId(), inputs(event.tenantId(), event.leadId()));
    }

    /** Every lead of the tenant (the refresh job). */
    @Transactional
    public int refreshTenant(Long tenantId) {
        List<Input> leads = inputs(tenantId, null);
        store(tenantId, leads);
        return leads.size();
    }

    private void store(Long tenantId, List<Input> leads) {
        List<LeadScore> scores = scoreAll(tenantId, leads);
        Map<Long, Input> before = new HashMap<>();
        leads.forEach(l -> before.put(l.id(), l));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        // Only scores that moved are written. Plain SQL: bookkeeping, not a user's change (no audit, no updated_at bump).
        jdbc.batchUpdate("UPDATE leads SET ai_score = ?, ai_score_label = ?, ai_scored_at = ? WHERE id = ?", scores.stream()
                .filter(s -> !Objects.equals(before.get(s.leadId()).storedScore(), s.score()) || !Objects.equals(before.get(s.leadId()).storedLabel(), s.label()))
                .map(s -> new Object[]{s.score(), s.label(), now, s.leadId()}).toList());
    }

    private List<Input> inputs(Long tenantId, Long leadId) {
        return jdbc.query("SELECT id, lead_name, company_name, industry, source, stage, created_at, budget, ai_score, ai_score_label FROM leads "
                        + "WHERE tenant_id = ?" + (leadId != null ? " AND id = ?" : ""),
                (rs, i) -> new Input(rs.getLong("id"), rs.getString("lead_name"), rs.getString("company_name"), rs.getString("industry"),
                        rs.getString("source"), rs.getString("stage"), rs.getTimestamp("created_at").toLocalDateTime(), rs.getBigDecimal("budget"),
                        (Integer) rs.getObject("ai_score"), rs.getString("ai_score_label")),
                leadId != null ? new Object[]{tenantId, leadId} : new Object[]{tenantId});
    }

    // ==================== The score ====================

    private List<LeadScore> scoreAll(Long tenantId, List<Input> leads) {
        if (leads.isEmpty()) {
            return List.of();
        }
        Map<String, long[]> byIndustry = conversionBy(tenantId, "industry");
        Map<String, long[]> bySource = conversionBy(tenantId, "source");
        long[] overall = byIndustry.values().stream().reduce(new long[]{0, 0}, (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
        double overallRate = overall[0] == 0 ? 0.2 : (double) overall[1] / overall[0];
        BigDecimal typicalWon = typicalWonDeal(tenantId);

        // NFR-7.1c: a few leads are looked up by id; many (beyond what fits in one IN list) are counted tenant-wide.
        List<Long> ids = leads.size() <= MAX_ID_LIST ? leads.stream().map(Input::id).toList() : null;
        String whereLead = ids != null ? "lead_id IN (:ids)" : "lead_id IS NOT NULL";
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> messages = countBy("SELECT lead_id, count(*) FROM communication_logs WHERE tenant_id = :t AND " + whereLead
                + " AND occurred_at >= :since GROUP BY lead_id", tenantId, ids, now.minusDays(30));
        Map<Long, Long> meetings = countBy("SELECT lead_id, count(*) FROM meetings WHERE tenant_id = :t AND " + whereLead
                + " AND status = 'scheduled' AND starts_at >= :since GROUP BY lead_id", tenantId, ids, now.minusDays(30));
        Map<Long, LocalDateTime> lastTouch = lastInteraction(tenantId, ids, whereLead, now);
        Map<Long, Long> purchases = pastPurchases(tenantId, ids);

        List<LeadScore> out = new ArrayList<>();
        for (Input lead : leads) {
            List<Factor> factors = new ArrayList<>();
            long[] ind = byIndustry.getOrDefault(String.valueOf(lead.industry()), new long[]{0, 0});
            double industryRate = smoothed(ind, overallRate);
            factors.add(new Factor("industry", relative(industryRate, overallRate), W_INDUSTRY,
                    pct(industryRate) + " of " + lead.industry() + " leads convert (" + ind[0] + " so far)"));
            long[] src = bySource.getOrDefault(String.valueOf(lead.source()), new long[]{0, 0});
            double sourceRate = smoothed(src, overallRate);
            factors.add(new Factor("source", relative(sourceRate, overallRate), W_SOURCE,
                    pct(sourceRate) + " of " + lead.source() + " leads convert (" + src[0] + " so far)"));
            factors.add(budgetFactor(lead.budget(), typicalWon));
            long bought = purchases.getOrDefault(lead.id(), 0L);
            factors.add(new Factor("buying", bought >= 2 ? 1.0 : bought == 1 ? 0.75 : 0.3, W_BUYING,
                    bought == 0 ? "no purchases by this company yet" : "this company has bought " + bought + " time(s) before"));
            long msg = messages.getOrDefault(lead.id(), 0L);
            long meet = meetings.getOrDefault(lead.id(), 0L);
            factors.add(new Factor("engagement", Math.min(1.0, (msg + 2.0 * meet) / 6.0), W_ENGAGEMENT,
                    msg + " message(s)/call(s) and " + meet + " meeting(s) in the last 30 days"));
            LocalDateTime touched = lastTouch.getOrDefault(lead.id(), lead.createdAt());
            long days = touched == null ? 60 : Math.max(0, Duration.between(touched, now).toDays());
            factors.add(new Factor("recency", days <= 3 ? 1.0 : days >= 60 ? 0.0 : 1.0 - (days - 3) / 57.0, W_RECENCY,
                    "last contact " + days + " day(s) ago"));
            int stageIndex = Math.max(0, STAGES.indexOf(lead.stage()));
            factors.add(new Factor("stage", stageIndex / (double) (STAGES.size() - 1), W_STAGE, "stage " + lead.stage()));

            double total = factors.stream().mapToDouble(f -> f.value() * f.weight()).sum();
            int score = (int) Math.round(Math.max(0, Math.min(1, total)) * 100);
            out.add(new LeadScore(lead.id(), lead.leadName(), lead.companyName(), lead.stage(), score, label(score),
                    factors.stream().map(f -> new Factor(f.name(), round2(f.value()), f.weight(), f.detail())).toList()));
        }
        return out;
    }

    /** At or above the tenant's typical (median) won deal = 1; half of it = 0.5; unknown budget = neutral. */
    static Factor budgetFactor(BigDecimal budget, BigDecimal typicalWon) {
        if (budget == null) {
            return new Factor("budget", 0.5, W_BUDGET, "budget not recorded");
        }
        if (typicalWon == null || typicalWon.signum() == 0) {
            return new Factor("budget", budget.signum() > 0 ? 0.6 : 0.2, W_BUDGET, "budget " + budget.stripTrailingZeros().toPlainString()
                    + " (no won deals yet to compare with)");
        }
        double ratio = budget.doubleValue() / typicalWon.doubleValue();
        return new Factor("budget", Math.min(1.0, ratio), W_BUDGET, "budget " + budget.stripTrailingZeros().toPlainString()
                + " vs a typical won deal of " + typicalWon.stripTrailingZeros().toPlainString());
    }

    static String label(int score) {
        return score >= 70 ? "High" : score >= 40 ? "Medium" : "Low";
    }

    /** Conversion counts {leads, converted} per value of a lead column, across the tenant. */
    private Map<String, long[]> conversionBy(Long tenantId, String column) {
        Map<String, long[]> out = new HashMap<>();
        jdbc.query("SELECT " + column + ", count(*), count(*) FILTER (WHERE stage = 'converted') FROM leads WHERE tenant_id = ? GROUP BY " + column,
                rs -> {
                    out.put(String.valueOf(rs.getString(1)), new long[]{rs.getLong(2), rs.getLong(3)});
                }, tenantId);
        return out;
    }

    /** The median value of deals won in the last two years. */
    private BigDecimal typicalWonDeal(Long tenantId) {
        return jdbc.queryForObject("SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY deal_value) FROM opportunities "
                + "WHERE tenant_id = ? AND stage = 'won' AND stage_changed_at >= ?", BigDecimal.class, tenantId,
                Timestamp.valueOf(LocalDateTime.now().minusYears(2)));
    }

    /**
     * Buying behaviour: deals the lead's company already won with us — the customer it was converted
     * into, or an existing customer with the same email or company name.
     */
    private Map<Long, Long> pastPurchases(Long tenantId, List<Long> ids) {
        MapSqlParameterSource params = new MapSqlParameterSource("t", tenantId).addValue("since", Timestamp.valueOf(LocalDateTime.now().minusYears(2)));
        if (ids != null) {
            params.addValue("ids", ids);
        }
        Map<Long, Long> out = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT l.id, count(DISTINCT o.id) FROM leads l
                JOIN customers c ON c.tenant_id = l.tenant_id AND (c.id = l.converted_customer_id
                     OR (l.email_hash IS NOT NULL AND c.email_hash = l.email_hash) OR lower(c.company_name) = lower(l.company_name))
                JOIN opportunities o ON o.customer_id = c.id AND o.stage = 'won' AND o.stage_changed_at >= :since
                WHERE l.tenant_id = :t""" + (ids != null ? " AND l.id IN (:ids)" : "") + " GROUP BY l.id", params, rs -> {
            out.put(rs.getLong(1), rs.getLong(2));
        });
        return out;
    }

    private Map<Long, Long> countBy(String sql, Long tenantId, List<Long> ids, LocalDateTime since) {
        MapSqlParameterSource params = new MapSqlParameterSource("t", tenantId).addValue("since", Timestamp.valueOf(since));
        if (ids != null) {
            params.addValue("ids", ids);
        }
        Map<Long, Long> out = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query(sql, params, rs -> {
            out.put(rs.getLong(1), rs.getLong(2));
        });
        return out;
    }

    private Map<Long, LocalDateTime> lastInteraction(Long tenantId, List<Long> ids, String whereLead, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource("t", tenantId).addValue("now", Timestamp.valueOf(now));
        if (ids != null) {
            params.addValue("ids", ids);
        }
        Map<Long, LocalDateTime> out = new HashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query("SELECT lead_id, max(at) FROM ("
                + " SELECT lead_id, occurred_at AS at FROM communication_logs WHERE tenant_id = :t AND " + whereLead
                + " UNION ALL SELECT lead_id, starts_at FROM meetings WHERE tenant_id = :t AND " + whereLead + " AND starts_at <= :now"
                + " AND status = 'scheduled') x GROUP BY lead_id", params, rs -> {
            Timestamp at = rs.getTimestamp(2);
            if (at != null) {
                out.put(rs.getLong(1), at.toLocalDateTime());
            }
        });
        return out;
    }

    /** Conversion rate pulled towards the tenant's overall rate when there are few leads to go on. */
    static double smoothed(long[] counts, double prior) {
        return (counts[1] + 2 * prior) / (counts[0] + 2);
    }

    /** 0.5 = as good as the tenant average; 1 = twice as good or better. */
    static double relative(double rate, double overall) {
        return Math.min(1.0, rate / (2 * Math.max(overall, 0.05)));
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
