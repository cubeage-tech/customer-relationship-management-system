package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.DealFactor;
import com.company.crm.ai.dto.AiDtos.DealProbability;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.entity.PipelineStage;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-AI-2.2: the chance each open deal closes as won. It starts from the stage — the tenant's win rate
 * over the last year, half of it in the first open stage up to 1.5× in the last — and is then moved by
 * what the history says about this deal: its owner's own win rate, how long it has been open against
 * how long won deals usually take, an expected closing date already passed, its size against a
 * typical won deal, whether the customer has bought before, and recent contact with the customer.
 * Each move is listed with its reason. Computed from CRM data — no language model.
 */
@Service
@RequiredArgsConstructor
public class DealProbabilityService {

    private static final double MIN = 0.02;
    private static final double MAX = 0.95;

    private final OpportunityService opportunityService;
    private final PipelineStageService pipelineStageService;
    private final JdbcTemplate jdbc;

    /** The tenant's history that every deal is measured against. */
    private record Context(double winRate, List<String> openStages, Map<Long, long[]> ownerRecord, Double typicalDaysToWin,
                           BigDecimal typicalWon, Map<Long, Long> customerWins, Map<Long, Long> recentContacts) {}

    /** Every open deal the caller can see, likeliest first. */
    @Transactional
    public List<DealProbability> probabilities(User user) {
        List<OpportunityResDto> deals = opportunityService.listOpenOpportunities(user);
        return score(user.getTenant().getId(), deals).stream()
                .sorted(Comparator.comparingDouble(DealProbability::probability).reversed())
                .toList();
    }

    /** One deal (the caller must be able to see it). */
    @Transactional
    public DealProbability probability(User user, Long opportunityId) {
        OpportunityResDto deal = opportunityService.getOpportunity(user, opportunityId);
        List<DealProbability> scored = score(user.getTenant().getId(), List.of(deal));
        if (scored.isEmpty()) {
            throw com.company.crm.common.exception.ApiException.badRequest("This deal is already closed (" + deal.getStage() + ")");
        }
        return scored.get(0);
    }

    /** The open deals among {@code deals}, scored (used by the forecast too, so both agree). */
    @Transactional
    public List<DealProbability> score(Long tenantId, List<OpportunityResDto> deals) {
        List<OpportunityResDto> open = deals.stream().filter(o -> !PipelineStageService.isTerminal(o.getStage())).toList();
        if (open.isEmpty()) {
            return List.of();
        }
        Context ctx = context(tenantId);
        LocalDate today = LocalDate.now();
        List<DealProbability> out = new ArrayList<>(open.size());
        for (OpportunityResDto o : open) {
            List<DealFactor> factors = new ArrayList<>();
            int index = ctx.openStages().indexOf(o.getStage());
            double p = stageChance(ctx.winRate(), index, ctx.openStages().size());
            factors.add(new DealFactor("stage", 1.0, "stage " + o.getStage() + ": " + pct(p) + " from your " + pct(ctx.winRate()) + " win rate"));

            long[] owner = o.getOwnerId() == null ? null : ctx.ownerRecord().get(o.getOwnerId());
            if (owner != null && owner[1] > 0) {
                double ownerRate = (owner[0] + 5 * ctx.winRate()) / (owner[1] + 5);
                double effect = clamp(ownerRate / ctx.winRate(), 0.6, 1.4);
                p *= effect;
                factors.add(new DealFactor("owner", round2(effect), "owner wins " + pct((double) owner[0] / owner[1]) + " of closed deals ("
                        + owner[1] + ")"));
            }
            if (ctx.typicalDaysToWin() != null && o.getCreatedAt() != null) {
                long age = ChronoUnit.DAYS.between(o.getCreatedAt().toLocalDate(), today);
                double typical = Math.max(1, ctx.typicalDaysToWin());
                double effect = age > 2 * typical ? 0.7 : age > 1.5 * typical ? 0.85 : 1.0;
                if (effect < 1) {
                    p *= effect;
                    factors.add(new DealFactor("age", effect, "open " + age + " days; won deals usually take " + Math.round(typical)));
                }
            }
            if (o.getExpectedClosingDate() != null && o.getExpectedClosingDate().isBefore(today)) {
                p *= 0.7;
                factors.add(new DealFactor("overdue", 0.7, "expected to close on " + o.getExpectedClosingDate() + " — already passed"));
            }
            if (ctx.typicalWon() != null && ctx.typicalWon().signum() > 0 && o.getDealValue() != null && o.getDealValue().signum() > 0) {
                double ratio = o.getDealValue().doubleValue() / ctx.typicalWon().doubleValue();
                double effect = ratio > 2 ? 0.85 : ratio < 0.5 ? 1.05 : 1.0;
                if (effect != 1) {
                    p *= effect;
                    factors.add(new DealFactor("size", effect, (ratio > 2 ? "over twice" : "under half") + " a typical won deal ("
                            + ctx.typicalWon().stripTrailingZeros().toPlainString() + ")"));
                }
            }
            long wins = ctx.customerWins().getOrDefault(o.getCustomerId(), 0L);
            if (wins > 0) {
                p *= 1.15;
                factors.add(new DealFactor("customer", 1.15, "repeat customer (" + wins + " won deal" + (wins == 1 ? "" : "s") + ")"));
            }
            long contacts = ctx.recentContacts().getOrDefault(o.getCustomerId(), 0L);
            double engagement = contacts > 0 ? 1.1 : 0.9;
            p *= engagement;
            factors.add(new DealFactor("engagement", engagement, contacts > 0 ? contacts + " contact(s) with the customer in the last 30 days"
                    : "no contact with the customer in the last 30 days"));

            double chance = round2(clamp(p, MIN, MAX)); // the shown chance is the one the expected value uses
            BigDecimal value = o.getDealValue() != null ? o.getDealValue() : BigDecimal.ZERO;
            int percent = (int) Math.round(chance * 100);
            out.add(new DealProbability(o.getId(), o.getCustomerName(), o.getOwnerName(), o.getStage(), value, o.getExpectedClosingDate(),
                    chance, percent, percent >= 60 ? "High" : percent >= 30 ? "Medium" : "Low",
                    value.multiply(BigDecimal.valueOf(chance)).setScale(2, RoundingMode.HALF_UP), factors));
        }
        return out;
    }

    /** Chance of closing in open stage {@code index} of {@code count}: from half to 1.5× the win rate. */
    static double stageChance(double winRate, int index, int count) {
        double position = count <= 1 || index < 0 ? 0.5 : (double) index / (count - 1);
        return Math.min(MAX, winRate * (0.5 + position));
    }

    private Context context(Long tenantId) {
        Timestamp yearAgo = Timestamp.valueOf(LocalDateTime.now().minusYears(1));
        Timestamp twoYearsAgo = Timestamp.valueOf(LocalDateTime.now().minusYears(2));
        Map<String, Object> closed = jdbc.queryForMap("SELECT count(*) FILTER (WHERE stage = 'won') AS won, count(*) AS closed FROM opportunities "
                + "WHERE tenant_id = ? AND stage IN ('won', 'lost') AND stage_changed_at >= ?", tenantId, yearAgo);
        // Pulled towards 30% when there are few closed deals.
        double winRate = (((Number) closed.get("won")).longValue() + 0.3 * 5) / (((Number) closed.get("closed")).longValue() + 5);
        List<String> openStages = pipelineStageService.stages(tenantId).stream()
                .filter(s -> s.isActive() && !PipelineStageService.isTerminal(s.getCode()))
                .map(PipelineStage::getCode).toList();

        Map<Long, long[]> owners = new HashMap<>();
        jdbc.query("SELECT owner_id, count(*) FILTER (WHERE stage = 'won'), count(*) FROM opportunities WHERE tenant_id = ? "
                + "AND stage IN ('won', 'lost') AND stage_changed_at >= ? AND owner_id IS NOT NULL GROUP BY owner_id", rs -> {
            owners.put(rs.getLong(1), new long[]{rs.getLong(2), rs.getLong(3)});
        }, tenantId, twoYearsAgo);
        Map<String, Object> won = jdbc.queryForMap("SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM stage_changed_at - created_at) / 86400) AS days, "
                + "percentile_cont(0.5) WITHIN GROUP (ORDER BY deal_value) AS value FROM opportunities "
                + "WHERE tenant_id = ? AND stage = 'won' AND stage_changed_at >= ?", tenantId, twoYearsAgo);
        Map<Long, Long> customerWins = new HashMap<>();
        jdbc.query("SELECT customer_id, count(*) FROM opportunities WHERE tenant_id = ? AND stage = 'won' GROUP BY customer_id", rs -> {
            customerWins.put(rs.getLong(1), rs.getLong(2));
        }, tenantId);
        Map<Long, Long> contacts = new HashMap<>();
        Timestamp monthAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
        jdbc.query("SELECT customer_id, count(*) FROM (SELECT customer_id FROM communication_logs WHERE tenant_id = ? AND customer_id IS NOT NULL "
                + "AND occurred_at >= ? UNION ALL SELECT customer_id FROM meetings WHERE tenant_id = ? AND customer_id IS NOT NULL AND status = 'scheduled' AND starts_at >= ? "
                + "AND starts_at <= now()) x GROUP BY customer_id", rs -> {
            contacts.put(rs.getLong(1), rs.getLong(2));
        }, tenantId, monthAgo, tenantId, monthAgo);
        Number days = (Number) won.get("days");
        Number value = (Number) won.get("value");
        return new Context(winRate, openStages, owners, days == null ? null : days.doubleValue(),
                value == null ? null : BigDecimal.valueOf(value.doubleValue()).setScale(2, RoundingMode.HALF_UP), customerWins, contacts);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
