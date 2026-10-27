package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.Factor;
import com.company.crm.ai.dto.AiDtos.LeadScore;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.service.LeadService;
import com.company.crm.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * FR-AI-1 / API-G11: a 0–100 lead score from the tenant's own history — how well leads of the same
 * industry and source have converted, how engaged the lead is (messages, calls, meetings in the
 * last 30 days), how recently it was contacted, and how far along the pipeline it is. Every score
 * comes with its factors, so a salesperson can see why. (Leads carry no budget field, so budget
 * isn't a factor yet.)
 */
@Service
@RequiredArgsConstructor
public class LeadScoringService {

    static final double W_INDUSTRY = 0.25;
    static final double W_SOURCE = 0.15;
    static final double W_ENGAGEMENT = 0.25;
    static final double W_RECENCY = 0.15;
    static final double W_STAGE = 0.20;
    private static final List<String> STAGES = List.of("new_lead", "contacted", "meeting", "quotation", "negotiation", "converted");

    private final LeadService leadService;

    @PersistenceContext
    private EntityManager entityManager;

    /** One lead (the caller must be able to see it). */
    @Transactional(readOnly = true)
    public LeadScore score(User user, Long leadId) {
        LeadResDto lead = leadService.getLead(user, leadId);
        return scoreAll(user, List.of(lead)).get(0);
    }

    /** Every lead the caller can see, highest score first (FR-AI-1.3); optional minimum score. */
    @Transactional(readOnly = true)
    public List<LeadScore> scores(User user, Integer minScore) {
        List<LeadResDto> leads = leadService.listLeads(user, null, null, null, null, null);
        return scoreAll(user, leads).stream()
                .filter(s -> minScore == null || s.score() >= minScore)
                .sorted(Comparator.comparingInt(LeadScore::score).reversed())
                .toList();
    }

    private List<LeadScore> scoreAll(User user, List<LeadResDto> leads) {
        if (leads.isEmpty()) {
            return List.of();
        }
        Long tenantId = user.getTenant().getId();
        Map<String, long[]> byIndustry = conversionBy(tenantId, "industry");
        Map<String, long[]> bySource = conversionBy(tenantId, "source");
        long[] overall = byIndustry.values().stream().reduce(new long[]{0, 0}, (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
        double overallRate = overall[0] == 0 ? 0.2 : (double) overall[1] / overall[0];

        // NFR-7.1c: a few leads are looked up by id; many (beyond what fits in one IN list) are counted tenant-wide.
        List<Long> ids = leads.size() <= MAX_ID_LIST ? leads.stream().map(LeadResDto::getId).toList() : null;
        String whereLead = ids != null ? "lead_id IN (:ids)" : "lead_id IS NOT NULL";
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> messages = countBy("SELECT lead_id, count(*) FROM communication_logs WHERE tenant_id = :t AND " + whereLead
                + " AND occurred_at >= :since GROUP BY lead_id", tenantId, ids, now.minusDays(30));
        Map<Long, Long> meetings = countBy("SELECT lead_id, count(*) FROM meetings WHERE tenant_id = :t AND " + whereLead
                + " AND status = 'scheduled' AND starts_at >= :since GROUP BY lead_id", tenantId, ids, now.minusDays(30));
        Map<Long, LocalDateTime> lastTouch = lastInteraction(tenantId, ids, whereLead, now);

        List<LeadScore> out = new ArrayList<>();
        for (LeadResDto lead : leads) {
            List<Factor> factors = new ArrayList<>();
            long[] ind = byIndustry.getOrDefault(String.valueOf(lead.getIndustry()), new long[]{0, 0});
            double industryRate = smoothed(ind, overallRate);
            factors.add(new Factor("industry", relative(industryRate, overallRate), W_INDUSTRY,
                    pct(industryRate) + " of " + lead.getIndustry() + " leads convert (" + ind[0] + " so far)"));
            long[] src = bySource.getOrDefault(String.valueOf(lead.getSource()), new long[]{0, 0});
            double sourceRate = smoothed(src, overallRate);
            factors.add(new Factor("source", relative(sourceRate, overallRate), W_SOURCE,
                    pct(sourceRate) + " of " + lead.getSource() + " leads convert (" + src[0] + " so far)"));
            long msg = messages.getOrDefault(lead.getId(), 0L);
            long meet = meetings.getOrDefault(lead.getId(), 0L);
            factors.add(new Factor("engagement", Math.min(1.0, (msg + 2.0 * meet) / 6.0), W_ENGAGEMENT,
                    msg + " message(s)/call(s) and " + meet + " meeting(s) in the last 30 days"));
            LocalDateTime touched = lastTouch.getOrDefault(lead.getId(), lead.getCreatedAt());
            long days = touched == null ? 60 : Math.max(0, Duration.between(touched, now).toDays());
            factors.add(new Factor("recency", days <= 3 ? 1.0 : days >= 60 ? 0.0 : 1.0 - (days - 3) / 57.0, W_RECENCY,
                    "last contact " + days + " day(s) ago"));
            int stageIndex = Math.max(0, STAGES.indexOf(lead.getStage()));
            factors.add(new Factor("stage", stageIndex / (double) (STAGES.size() - 1), W_STAGE, "stage " + lead.getStage()));

            double total = factors.stream().mapToDouble(f -> f.value() * f.weight()).sum();
            int score = (int) Math.round(Math.max(0, Math.min(1, total)) * 100);
            out.add(new LeadScore(lead.getId(), lead.getLeadName(), lead.getCompanyName(), lead.getStage(), score, label(score),
                    factors.stream().map(f -> new Factor(f.name(), round2(f.value()), f.weight(), f.detail())).toList()));
        }
        return out;
    }

    /** Ids per IN list, well under the database's limit on parameters per statement. */
    private static final int MAX_ID_LIST = 1000;

    static String label(int score) {
        return score >= 70 ? "High" : score >= 40 ? "Medium" : "Low";
    }

    /** Conversion counts {leads, converted} per value of a lead column, across the tenant. */
    @SuppressWarnings("unchecked")
    private Map<String, long[]> conversionBy(Long tenantId, String column) {
        List<Object[]> rows = entityManager.createNativeQuery("SELECT " + column + ", count(*), count(*) FILTER (WHERE stage = 'converted')"
                + " FROM leads WHERE tenant_id = :t GROUP BY " + column).setParameter("t", tenantId).getResultList();
        Map<String, long[]> out = new HashMap<>();
        rows.forEach(r -> out.put(String.valueOf(r[0]), new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()}));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> countBy(String sql, Long tenantId, List<Long> ids, LocalDateTime since) {
        var query = entityManager.createNativeQuery(sql).setParameter("t", tenantId).setParameter("since", since);
        if (ids != null) {
            query.setParameter("ids", ids);
        }
        List<Object[]> rows = query.getResultList();
        Map<Long, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, LocalDateTime> lastInteraction(Long tenantId, List<Long> ids, String whereLead, LocalDateTime now) {
        var query = entityManager.createNativeQuery("SELECT lead_id, max(at) FROM ("
                        + " SELECT lead_id, occurred_at AS at FROM communication_logs WHERE tenant_id = :t AND " + whereLead
                        + " UNION ALL SELECT lead_id, starts_at FROM meetings WHERE tenant_id = :t AND " + whereLead + " AND starts_at <= :now"
                        + " AND status = 'scheduled') x GROUP BY lead_id")
                .setParameter("t", tenantId).setParameter("now", now);
        if (ids != null) {
            query.setParameter("ids", ids);
        }
        List<Object[]> rows = query.getResultList();
        Map<Long, LocalDateTime> out = new HashMap<>();
        for (Object[] r : rows) {
            Object v = r[1];
            out.put(((Number) r[0]).longValue(), v instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) v);
        }
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
