package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.ContactTime;
import com.company.crm.ai.dto.AiDtos.DealProbability;
import com.company.crm.ai.dto.AiDtos.LeadScore;
import com.company.crm.campaign.service.OfferRecommendationService;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.lead.dto.response.LeadResDto;
import com.company.crm.lead.service.LeadService;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FR-AI-3.3 / FR-AI-5.1: what the CRM knows about a customer, lead or deal, as short facts for the
 * language model — industry, purchases, open deals and their chance, quotations, tickets, recent
 * contact, and the statistical suggestions (products, offers, best contact time). Business facts
 * only: no email addresses, phone numbers or message text (SEC-5). The caller has already checked
 * the user may see the record.
 */
@Component
@RequiredArgsConstructor
public class CustomerFacts {

    private final JdbcTemplate jdbc;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final OpportunityService opportunityService;
    private final DealProbabilityService dealProbabilities;
    private final ProductRecommendationService productRecommendations;
    private final OfferRecommendationService offerRecommendations;
    private final ContactTimeService contactTimes;
    private final LeadScoringService leadScoring;

    public List<String> customer(User user, Long customerId) {
        CustomerResDto c = customerService.getCustomer(user, customerId);
        Long tenantId = user.getTenant().getId();
        List<String> facts = new ArrayList<>();
        facts.add("Customer: " + c.getCompanyName() + ", industry " + c.getIndustry() + ", status " + c.getStatus());

        Timestamp twoYears = Timestamp.valueOf(LocalDateTime.now().minusYears(2));
        Map<String, Object> won = jdbc.queryForMap("SELECT count(*) AS n, coalesce(sum(deal_value), 0) AS total, max(stage_changed_at) AS last "
                + "FROM opportunities WHERE tenant_id = ? AND customer_id = ? AND stage = 'won' AND stage_changed_at >= ?", tenantId, customerId, twoYears);
        long wins = ((Number) won.get("n")).longValue();
        facts.add(wins == 0 ? "No won deals in the last two years"
                : wins + " won deal(s) in the last two years, worth " + plain((BigDecimal) won.get("total")) + ", last on "
                + ((Timestamp) won.get("last")).toLocalDateTime().toLocalDate());
        List<String> bought = jdbc.queryForList("SELECT i.product_name FROM quotation_line_items i JOIN quotations q ON q.id = i.quotation_id "
                + "WHERE q.tenant_id = ? AND q.customer_id = ? AND q.status = 'approved' GROUP BY i.product_name ORDER BY max(q.created_at) DESC LIMIT 5",
                String.class, tenantId, customerId);
        if (!bought.isEmpty()) {
            facts.add("Has bought: " + String.join(", ", bought));
        }

        List<OpportunityResDto> deals = opportunityService.listOpportunities(user, null, customerId, null);
        for (DealProbability d : dealProbabilities.score(tenantId, deals).stream().limit(3).toList()) {
            facts.add("Open deal: " + (d.stage()) + ", value " + plain(d.dealValue()) + ", " + d.percent() + "% chance to win ("
                    + d.label() + ")" + (d.expectedClosingDate() != null ? ", expected to close " + d.expectedClosingDate() : ""));
        }
        Map<String, Object> quotes = jdbc.queryForMap("SELECT count(DISTINCT q.id) AS n, coalesce(sum(i.quantity * i.unit_price * (1 - i.discount_percent / 100)), 0) AS total "
                + "FROM quotations q LEFT JOIN quotation_line_items i ON i.quotation_id = q.id "
                + "WHERE q.tenant_id = ? AND q.customer_id = ? AND q.status IN ('pending', 'viewed')", tenantId, customerId);
        if (((Number) quotes.get("n")).longValue() > 0) {
            facts.add(quotes.get("n") + " quotation(s) awaiting the customer's answer, worth " + plain((BigDecimal) quotes.get("total")));
        }
        Map<String, Object> tickets = jdbc.queryForMap("SELECT count(*) AS n, count(*) FILTER (WHERE priority IN ('high', 'critical')) AS urgent "
                + "FROM service_tickets WHERE tenant_id = ? AND customer_id = ? AND status NOT IN ('resolved', 'closed')", tenantId, customerId);
        if (((Number) tickets.get("n")).longValue() > 0) {
            facts.add(tickets.get("n") + " open service ticket(s), " + tickets.get("urgent") + " high or critical — acknowledge them");
        }
        lastContact(tenantId, "customer_id", customerId, facts);
        productRecommendations.recommend(user, customerId).recommendations().stream().limit(3)
                .forEach(r -> facts.add("Could also offer: " + r.productName() + " (" + r.reason() + ")"));
        offerRecommendations.recommend(user, customerId).offers().stream().limit(2)
                .forEach(o -> facts.add("Current offer that suits them: " + o.name() + " (" + o.discountLabel() + ")"));
        return facts;
    }

    public List<String> lead(User user, Long leadId) {
        LeadResDto l = leadService.getLead(user, leadId);
        List<String> facts = new ArrayList<>();
        facts.add("Lead: " + l.getLeadName() + " at " + l.getCompanyName() + ", industry " + l.getIndustry() + ", source " + l.getSource()
                + ", stage " + l.getStage() + (l.getBudget() != null ? ", budget " + plain(l.getBudget()) : ""));
        LeadScore score = leadScoring.score(user, leadId);
        facts.add("AI lead score " + score.score() + " (" + score.label() + ")");
        score.factors().stream().sorted((a, b) -> Double.compare(b.value() * b.weight(), a.value() * a.weight())).limit(3)
                .forEach(f -> facts.add("Score factor — " + f.name() + ": " + f.detail()));
        lastContact(user.getTenant().getId(), "lead_id", leadId, facts);
        return facts;
    }

    public List<String> deal(User user, Long opportunityId) {
        OpportunityResDto o = opportunityService.getOpportunity(user, opportunityId);
        List<String> facts = new ArrayList<>();
        facts.add("Deal: " + (o.getProductService() != null ? o.getProductService() : "unspecified product") + ", value " + plain(o.getDealValue())
                + ", stage " + o.getStage() + (o.getExpectedClosingDate() != null ? ", expected to close " + o.getExpectedClosingDate() : ""));
        if (!PipelineStageService.isTerminal(o.getStage())) {
            DealProbability p = dealProbabilities.probability(user, opportunityId);
            facts.add("Chance to win " + p.percent() + "% (" + p.label() + ")");
            p.factors().stream().filter(f -> f.effect() != 1.0).forEach(f -> facts.add("Deal factor — " + f.detail()));
        }
        facts.addAll(customer(user, o.getCustomerId()));
        return facts;
    }

    /** The best window to reach them, as a fact (or null). */
    public String bestTime(User user, Long customerId, Long leadId) {
        ContactTime t = contactTimes.bestTime(user, customerId, leadId);
        return t.windows().isEmpty() ? null : t.windows().get(0).label() + " (" + t.basis() + ")";
    }

    private void lastContact(Long tenantId, String column, Long id, List<String> facts) {
        Map<String, Object> last = jdbc.queryForMap("SELECT max(occurred_at) AS at, count(*) FILTER (WHERE occurred_at >= now() - interval '30 days') AS recent "
                + "FROM communication_logs WHERE tenant_id = ? AND " + column + " = ?", tenantId, id);
        Timestamp at = (Timestamp) last.get("at");
        facts.add(at == null ? "No messages or calls logged yet"
                : "Last contact " + at.toLocalDateTime().toLocalDate() + "; " + last.get("recent") + " message(s)/call(s) in the last 30 days");
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
