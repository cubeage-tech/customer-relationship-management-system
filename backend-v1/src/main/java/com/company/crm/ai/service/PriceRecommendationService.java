package com.company.crm.ai.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.quotation.entity.Product;
import com.company.crm.quotation.repository.ProductRepository;
import com.company.crm.tenant.service.TenantSettingsService;
import com.company.crm.user.entity.User;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * FR-4.7: a price recommendation per product (and customer) from the tenant's own quotation history.
 * Past quotes for the product that were decided — won (approved by the customer, or their deal won)
 * or lost (rejected, expired, or their deal lost) — are grouped by discount level. Each level's win
 * rate is smoothed toward the product's overall rate, so a level with few quotes isn't over-trusted,
 * and the level with the best expected net revenue (win rate × price after discount) is recommended.
 * This customer's own quotes count three times. Computed from CRM data — no language model.
 */
@Service
@RequiredArgsConstructor
public class PriceRecommendationService {

    private static final int HISTORY_DAYS = 730;
    private static final double CUSTOMER_WEIGHT = 3;
    private static final double PRIOR_STRENGTH = 2;
    private static final int MAX_PRODUCTS = 60;

    /** Discount levels, as [from, to] percent: none, up to 5, 5–10, 10–15, 15–20, over 20. */
    private static final double[][] LEVELS = {{0, 0}, {0, 5}, {5, 10}, {10, 15}, {15, 20}, {20, 100}};
    private static final String[] LEVEL_LABELS = {"No discount", "Up to 5%", "5–10%", "10–15%", "15–20%", "Over 20%"};

    private final JdbcTemplate jdbc;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final TenantSettingsService tenantSettings;

    public record PriceRecReq(Long customerId, @NotEmpty @Size(max = MAX_PRODUCTS) List<String> productNames) {}

    /** One discount level's track record (weighted counts; this customer's quotes count three times). */
    public record Level(String label, double quotes, double won, double winRate) {}

    public record PriceRec(String productName, BigDecimal listPrice, BigDecimal recommendedDiscountPercent, BigDecimal recommendedNetPrice,
                           Double expectedWinRate, int decidedQuotes, int wonQuotes, int customerQuotes, String confidence,
                           boolean needsApproval, String explanation, List<Level> levels) {}

    /** A decided quote line: its discount, whether it was won, whether it was this customer's. */
    record Row(double discount, BigDecimal unitPrice, boolean won, boolean customers) {}

    @Transactional(readOnly = true)
    public List<PriceRec> recommend(User user, PriceRecReq req) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Price recommendations are per tenant");
        }
        Long tenantId = user.getTenant().getId();
        if (req.customerId() != null) {
            customerService.getCustomer(user, req.customerId()); // 404/403 when out of scope
        }
        List<String> names = req.productNames().stream().filter(Objects::nonNull).map(String::trim).filter(n -> !n.isEmpty())
                .distinct().limit(MAX_PRODUCTS).toList();
        if (names.isEmpty()) {
            return List.of();
        }
        Map<String, List<Row>> history = history(tenantId, req.customerId(), names);
        Map<String, Product> catalog = new HashMap<>();
        productRepository.findByTenantId(tenantId).forEach(p -> catalog.putIfAbsent(key(p.getName()), p));
        BigDecimal threshold = tenantSettings.discountApprovalThreshold(tenantId);

        List<PriceRec> recs = new ArrayList<>();
        for (String name : names) {
            Product product = catalog.get(key(name));
            recs.add(recommend(name, product, history.getOrDefault(key(name), List.of()), threshold));
        }
        return recs;
    }

    /** The tenant's decided quote lines for these products in the last two years, in one query. */
    Map<String, List<Row>> history(Long tenantId, Long customerId, List<String> names) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("customerId", customerId == null ? -1L : customerId)
                .addValue("names", names.stream().map(PriceRecommendationService::key).toList())
                .addValue("since", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(HISTORY_DAYS)));
        Map<String, List<Row>> byProduct = new LinkedHashMap<>();
        new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT lower(i.product_name) AS product, i.discount_percent, i.unit_price, q.customer_id = :customerId AS customers,
                       CASE WHEN q.status = 'approved' OR o.stage = 'won' THEN true ELSE false END AS won
                FROM quotation_line_items i
                JOIN quotations q ON q.id = i.quotation_id
                LEFT JOIN opportunities o ON o.id = q.opportunity_id
                WHERE q.tenant_id = :tenantId AND q.created_at >= :since AND lower(i.product_name) IN (:names)
                  AND (q.status IN ('approved', 'rejected', 'expired') OR o.stage IN ('won', 'lost'))""", params, rs -> {
            byProduct.computeIfAbsent(rs.getString("product"), k -> new ArrayList<>()).add(new Row(rs.getDouble("discount_percent"),
                    rs.getBigDecimal("unit_price"), rs.getBoolean("won"), rs.getBoolean("customers")));
        });
        return byProduct;
    }

    static PriceRec recommend(String name, Product product, List<Row> rows, BigDecimal threshold) {
        BigDecimal listPrice = product != null ? product.getUnitPrice()
                : rows.stream().map(Row::unitPrice).filter(Objects::nonNull).reduce((a, b) -> b).orElse(null);
        int decided = rows.size();
        int won = (int) rows.stream().filter(Row::won).count();
        int customerQuotes = (int) rows.stream().filter(Row::customers).count();
        if (decided == 0) {
            return new PriceRec(name, listPrice, BigDecimal.ZERO, listPrice, null, 0, 0, 0, "none", false,
                    "No decided quotes for this product yet — start at the list price.", List.of());
        }

        double totalWeight = 0;
        double totalWon = 0;
        double[] weight = new double[LEVELS.length];
        double[] wins = new double[LEVELS.length];
        double[] wonDiscountSum = new double[LEVELS.length];
        double[] discountSum = new double[LEVELS.length];
        for (Row r : rows) {
            int level = level(r.discount());
            double w = r.customers() ? CUSTOMER_WEIGHT : 1;
            weight[level] += w;
            discountSum[level] += w * r.discount();
            totalWeight += w;
            if (r.won()) {
                wins[level] += w;
                wonDiscountSum[level] += w * r.discount();
                totalWon += w;
            }
        }
        // The product's own win rate, itself pulled gently toward 50%.
        double prior = (totalWon + 1) / (totalWeight + 2);

        List<Level> levels = new ArrayList<>();
        int best = -1;
        double bestValue = -1;
        double[] rate = new double[LEVELS.length];
        double[] typical = new double[LEVELS.length];
        for (int l = 0; l < LEVELS.length; l++) {
            if (weight[l] == 0) {
                continue;
            }
            rate[l] = (wins[l] + PRIOR_STRENGTH * prior) / (weight[l] + PRIOR_STRENGTH);
            // The discount to quote at this level: what won quotes there used, else the level's average.
            typical[l] = wins[l] > 0 ? wonDiscountSum[l] / wins[l] : discountSum[l] / weight[l];
            double value = rate[l] * (1 - typical[l] / 100);
            levels.add(new Level(LEVEL_LABELS[l], round1(weight[l]), round1(wins[l]), round3(rate[l])));
            if (value > bestValue + 1e-9 || (Math.abs(value - bestValue) <= 1e-9 && typical[l] < typical[best])) {
                best = l;
                bestValue = value;
            }
        }

        BigDecimal discount = BigDecimal.valueOf(Math.round(typical[best] * 2) / 2.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = listPrice == null ? null
                : listPrice.multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        String confidence = decided < 3 ? "low" : decided < 10 ? "medium" : "high";
        boolean needsApproval = threshold != null && discount.compareTo(threshold) > 0;
        return new PriceRec(name, listPrice, discount, net, round3(rate[best]), decided, won, customerQuotes, confidence, needsApproval,
                explain(best, discount, rate, weight, wins, customerQuotes, rows), levels);
    }

    private static String explain(int best, BigDecimal discount, double[] rate, double[] weight, double[] wins, int customerQuotes,
                                  List<Row> rows) {
        StringBuilder text = new StringBuilder();
        text.append(discount.signum() == 0 ? "At the list price" : "At about " + discount.stripTrailingZeros().toPlainString() + "% off")
                .append(", similar quotes were won ").append(Math.round(rate[best] * 100)).append("% of the time (")
                .append(count(wins[best])).append(" of ").append(count(weight[best])).append(")");
        int other = -1;
        for (int l = 0; l < LEVELS.length; l++) {
            if (l != best && weight[l] > 0 && (other < 0 || weight[l] > weight[other])) {
                other = l;
            }
        }
        if (other >= 0) {
            text.append(", vs ").append(Math.round(rate[other] * 100)).append("% at ").append(LEVEL_LABELS[other].toLowerCase(Locale.ROOT));
        }
        text.append('.');
        if (customerQuotes > 0) {
            long customerWon = rows.stream().filter(r -> r.customers() && r.won()).count();
            text.append(" This customer accepted ").append(customerWon).append(" of ").append(customerQuotes).append(" earlier quote")
                    .append(customerQuotes == 1 ? "" : "s").append(" for it.");
        }
        return text.toString();
    }

    private static int level(double discount) {
        if (discount <= 0) {
            return 0;
        }
        for (int l = 1; l < LEVELS.length; l++) {
            if (discount <= LEVELS[l][1]) {
                return l;
            }
        }
        return LEVELS.length - 1;
    }

    private static String count(double weighted) {
        return weighted == Math.rint(weighted) ? String.valueOf((long) weighted) : String.valueOf(round1(weighted));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    static String key(String productName) {
        return productName == null ? "" : productName.trim().toLowerCase(Locale.ROOT);
    }
}
