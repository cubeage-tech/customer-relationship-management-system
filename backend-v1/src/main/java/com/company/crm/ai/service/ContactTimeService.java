package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.ContactTime;
import com.company.crm.ai.dto.AiDtos.ContactWindow;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.lead.service.LeadService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FR-AI-3.2: the best time windows to contact a customer or lead — weekday and two-hour slot — from
 * when they have actually engaged: messages they sent, calls that connected (unanswered calls count
 * against the slot), campaign emails they opened or clicked, and meetings held. With little history
 * the pattern of similar customers (same industry) and then ordinary business hours fill in, and
 * the answer says which. Times are the CRM server's local time. Computed from CRM data — no
 * language model.
 */
@Service
@RequiredArgsConstructor
public class ContactTimeService {

    static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    /** Two-hour slots from 08:00 to 20:00; activity outside them counts towards the nearest one. */
    static final int FIRST_HOUR = 8;
    static final int SLOTS = 6;
    private static final int HISTORY_YEARS = 2;
    /** How many interactions' worth the fallback pattern is worth. */
    private static final double PRIOR_WEIGHT = 5;

    private final JdbcTemplate jdbc;
    private final CustomerService customerService;
    private final LeadService leadService;

    @Transactional(readOnly = true)
    public ContactTime bestTime(User user, Long customerId, Long leadId) {
        if ((customerId == null) == (leadId == null)) {
            throw ApiException.badRequest("Give a customerId or a leadId");
        }
        Long tenantId = user.getTenant().getId();
        String industry;
        String column;
        Long id;
        if (customerId != null) {
            industry = customerService.getCustomer(user, customerId).getIndustry(); // scope check
            column = "customer_id";
            id = customerId;
        } else {
            industry = leadService.getLead(user, leadId).getIndustry();
            column = "lead_id";
            id = leadId;
        }
        Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusYears(HISTORY_YEARS));

        double[][] own = grid();
        long[] counts = new long[2]; // {positive interactions, unanswered calls}
        events(tenantId, "x." + column + " = ?", id, since, own, counts);
        double[][] similar = grid();
        events(tenantId, customerId != null
                ? "x.customer_id IN (SELECT id FROM customers WHERE tenant_id = " + tenantId + " AND industry = ?)"
                : "x.lead_id IN (SELECT id FROM leads WHERE tenant_id = " + tenantId + " AND industry = ?)", industry, since, similar, new long[2]);
        double similarTotal = total(similar);

        // The fallback pattern: similar customers when there are enough of them, else business hours.
        double[][] prior = similarTotal >= 10 ? normalise(similar) : normalise(businessHours());
        double[][] score = grid();
        for (int d = 0; d < 7; d++) {
            for (int s = 0; s < SLOTS; s++) {
                score[d][s] = Math.max(0, own[d][s]) + PRIOR_WEIGHT * prior[d][s];
            }
        }
        double sum = total(score);
        List<ContactWindow> windows = new ArrayList<>();
        boolean[][] taken = new boolean[7][SLOTS];
        for (int pick = 0; pick < 2; pick++) {
            int bestD = -1;
            int bestS = -1;
            for (int d = 0; d < 7; d++) {
                for (int s = 0; s < SLOTS; s++) {
                    if (!taken[d][s] && (bestD < 0 || score[d][s] > score[bestD][bestS])) {
                        bestD = d;
                        bestS = s;
                    }
                }
            }
            taken[bestD][bestS] = true;
            int from = FIRST_HOUR + 2 * bestS;
            windows.add(new ContactWindow(DAYS[bestD], String.format("%02d:00", from), String.format("%02d:00", from + 2),
                    DAYS[bestD] + " " + String.format("%02d:00–%02d:00", from, from + 2),
                    sum == 0 ? 0 : Math.round(score[bestD][bestS] / sum * 1000) / 10.0));
        }

        String basis = counts[0] >= 3 ? "from " + counts[0] + " of their past interactions"
                : similarTotal >= 10 ? (counts[0] > 0 ? "from their " + counts[0] + " interaction(s) and " : "from ")
                + "when other " + industry + " " + (customerId != null ? "customers" : "leads") + " engage"
                : "typical business hours (little history yet)";
        List<String> notes = new ArrayList<>();
        if (counts[1] > 0) {
            notes.add(counts[1] + " call(s) went unanswered — those slots count less.");
        }
        notes.add("Times are the CRM server's local time.");
        return new ContactTime(customerId != null ? "customer" : "lead", id, windows, basis, counts[0], notes);
    }

    /** Adds interactions matching {@code where} to the grid; counts {positive, unanswered calls}. */
    private void events(Long tenantId, String where, Object param, Timestamp since, double[][] grid, long[] counts) {
        String sql = """
                SELECT EXTRACT(ISODOW FROM at)::int AS dow, EXTRACT(HOUR FROM at)::int AS hour, sum(weight) AS weight, count(*) AS n,
                       count(*) FILTER (WHERE weight < 0) AS unanswered
                FROM (
                    SELECT occurred_at AS at, customer_id, lead_id,
                           CASE WHEN direction = 'inbound' THEN 1.0
                                WHEN channel = 'call' AND call_outcome = 'connected' THEN 1.0
                                WHEN channel = 'call' AND call_outcome IN ('no_answer', 'busy', 'voicemail') THEN -0.5
                                WHEN status = 'read' THEN 0.5 ELSE 0 END AS weight
                    FROM communication_logs WHERE tenant_id = ? AND occurred_at >= ?
                    UNION ALL
                    SELECT starts_at, customer_id, lead_id, 0.8 FROM meetings
                    WHERE tenant_id = ? AND status = 'scheduled' AND starts_at >= ? AND starts_at <= now()
                    UNION ALL
                    SELECT opened_at, customer_id, NULL::bigint, 0.7 FROM campaign_recipients WHERE tenant_id = ? AND opened_at >= ?
                    UNION ALL
                    SELECT clicked_at, customer_id, NULL::bigint, 0.7 FROM campaign_recipients WHERE tenant_id = ? AND clicked_at >= ?
                ) x
                WHERE weight <> 0""" + " AND " + where + " GROUP BY 1, 2"; // text blocks drop trailing spaces
        jdbc.query(sql, rs -> {
            int day = rs.getInt("dow") - 1;
            int slot = Math.max(0, Math.min(SLOTS - 1, (rs.getInt("hour") - FIRST_HOUR) / 2));
            grid[day][slot] += rs.getDouble("weight");
            counts[0] += rs.getLong("n") - rs.getLong("unanswered");
            counts[1] += rs.getLong("unanswered");
        }, tenantId, since, tenantId, since, tenantId, since, tenantId, since, param);
    }

    /** Weekdays 10–12 and 14–16 best, other weekday slots fair, Saturday poor, Sunday hardly. */
    static double[][] businessHours() {
        double[][] g = grid();
        for (int d = 0; d < 7; d++) {
            for (int s = 0; s < SLOTS; s++) {
                g[d][s] = d == 6 ? 0.05 : d == 5 ? 0.2 : (s == 1 || s == 3) ? 1.0 : s == 5 ? 0.3 : 0.6;
                if (d >= 1 && d <= 3 && (s == 1 || s == 3)) {
                    g[d][s] = 1.1; // mid-week mornings and afternoons
                }
            }
        }
        return g;
    }

    private static double[][] normalise(double[][] g) {
        double[][] out = grid();
        double sum = 0;
        for (double[] row : g) {
            for (double v : row) {
                sum += Math.max(0, v);
            }
        }
        for (int d = 0; d < 7; d++) {
            for (int s = 0; s < SLOTS; s++) {
                out[d][s] = sum == 0 ? 1.0 / (7 * SLOTS) : Math.max(0, g[d][s]) / sum;
            }
        }
        return out;
    }

    private static double total(double[][] g) {
        double sum = 0;
        for (double[] row : g) {
            for (double v : row) {
                sum += Math.max(0, v);
            }
        }
        return sum;
    }

    private static double[][] grid() {
        return new double[7][SLOTS];
    }
}
