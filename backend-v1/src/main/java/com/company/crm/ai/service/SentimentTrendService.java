package com.company.crm.ai.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-AI-6.3: a customer's sentiment over time — month by month, how many of their messages and
 * reviews the AI read as positive, neutral or negative (FR-AI-6.1) and the average score, plus
 * whether it is improving or declining (the last three months against the three before). Uses
 * only the stored readings, so nothing is sent to the AI provider.
 */
@Service
@RequiredArgsConstructor
public class SentimentTrendService {

    private static final int MIN_MONTHS = 3;
    private static final int MAX_MONTHS = 24;
    /** A change in average score this large counts as improving or declining. */
    private static final double SHIFT = 0.2;

    public record TrendMonth(String month, long positive, long neutral, long negative, Double averageScore) {}

    /**
     * {@code direction}: improving | declining | steady | unknown (too few readings to compare);
     * {@code unclassified}: messages in the period not yet read, which "Check sentiment" can fill in.
     */
    public record SentimentTrend(Long customerId, int months, List<TrendMonth> series, long readings, long fromMessages, long fromReviews,
                                 Double averageScore, Double recentAverage, Double earlierAverage, String direction, String summary,
                                 long unclassified) {}

    private final JdbcTemplate jdbc;
    private final CustomerService customerService;

    @Transactional(readOnly = true)
    public SentimentTrend trend(User user, Long customerId, Integer months) {
        int n = months == null ? 6 : months;
        if (n < MIN_MONTHS || n > MAX_MONTHS) {
            throw ApiException.badRequest("months must be between " + MIN_MONTHS + " and " + MAX_MONTHS);
        }
        CustomerResDto customer = customerService.getCustomer(user, customerId);
        Long tenantId = user.getTenant().getId();
        YearMonth last = YearMonth.now();
        YearMonth first = last.minusMonths(n - 1L);
        Timestamp from = Timestamp.valueOf(first.atDay(1).atStartOfDay());

        // month, sentiment → count and score total; plus how many came from messages vs reviews
        Map<YearMonth, double[]> byMonth = new HashMap<>(); // positive, neutral, negative, score sum, count
        long[] sources = new long[2];
        jdbc.query("""
                        SELECT date_trunc('month', at)::date AS month, sentiment, count(*) AS n, sum(score) AS total, source FROM (
                            SELECT occurred_at AS at, sentiment, sentiment_score AS score, 'message' AS source FROM communication_logs
                            WHERE tenant_id = ? AND customer_id = ? AND direction = 'inbound' AND sentiment IS NOT NULL AND occurred_at >= ?
                            UNION ALL
                            SELECT feedback_at, feedback_sentiment, feedback_sentiment_score, 'review' FROM service_tickets
                            WHERE tenant_id = ? AND customer_id = ? AND feedback_sentiment IS NOT NULL AND feedback_at >= ?
                        ) r GROUP BY 1, 2, 5""",
                rs -> {
                    YearMonth m = YearMonth.from(rs.getDate("month").toLocalDate());
                    double[] v = byMonth.computeIfAbsent(m, k -> new double[5]);
                    long count = rs.getLong("n");
                    switch (rs.getString("sentiment")) {
                        case "positive" -> v[0] += count;
                        case "negative" -> v[2] += count;
                        default -> v[1] += count;
                    }
                    BigDecimal total = rs.getBigDecimal("total");
                    v[3] += total == null ? 0 : total.doubleValue();
                    v[4] += count;
                    sources["review".equals(rs.getString("source")) ? 1 : 0] += count;
                },
                tenantId, customer.getId(), from, tenantId, customer.getId(), from);

        List<TrendMonth> series = new ArrayList<>();
        double[] all = new double[2];
        double[] recent = new double[2];
        double[] earlier = new double[2];
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            double[] v = byMonth.getOrDefault(m, new double[5]);
            series.add(new TrendMonth(m.toString(), (long) v[0], (long) v[1], (long) v[2], v[4] == 0 ? null : round(v[3] / v[4])));
            add(all, v);
            long back = m.until(last, ChronoUnit.MONTHS);
            add(back < 3 ? recent : back < 6 ? earlier : new double[2], v);
        }
        Double recentAvg = avg(recent);
        Double earlierAvg = avg(earlier);
        String direction = recentAvg == null || earlierAvg == null ? "unknown"
                : recentAvg - earlierAvg >= SHIFT ? "improving" : earlierAvg - recentAvg >= SHIFT ? "declining" : "steady";
        long unclassified = jdbc.queryForObject("SELECT count(*) FROM communication_logs WHERE tenant_id = ? AND customer_id = ? "
                + "AND direction = 'inbound' AND channel IN ('email', 'whatsapp', 'sms') AND sentiment IS NULL AND occurred_at >= ?",
                Long.class, tenantId, customer.getId(), from);
        long readings = (long) all[1];
        return new SentimentTrend(customer.getId(), n, series, readings, sources[0], sources[1], avg(all), recentAvg, earlierAvg, direction,
                summary(readings, avg(all), recentAvg, earlierAvg, direction, n), unclassified);
    }

    static String summary(long readings, Double average, Double recent, Double earlier, String direction, int months) {
        if (readings == 0) {
            return "No messages or reviews have been read for sentiment in the last " + months + " months.";
        }
        String mood = average >= 0.25 ? "mostly positive" : average <= -0.25 ? "mostly negative" : "mixed";
        String base = "Over " + months + " months " + readings + " reading(s), " + mood + " (average " + fmt(average) + ")";
        return switch (direction) {
            case "improving" -> base + "; improving — " + fmt(recent) + " in the last 3 months, up from " + fmt(earlier) + ".";
            case "declining" -> base + "; declining — " + fmt(recent) + " in the last 3 months, down from " + fmt(earlier) + ".";
            case "steady" -> base + "; steady over the last 6 months.";
            default -> base + ".";
        };
    }

    private static void add(double[] into, double[] v) {
        into[0] += v[3];
        into[1] += v[4];
    }

    private static Double avg(double[] sumAndCount) {
        return sumAndCount[1] == 0 ? null : round(sumAndCount[0] / sumAndCount[1]);
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String fmt(Double v) {
        return (v > 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", v);
    }
}
