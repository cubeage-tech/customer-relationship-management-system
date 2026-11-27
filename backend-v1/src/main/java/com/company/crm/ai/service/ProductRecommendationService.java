package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.ProductRec;
import com.company.crm.ai.dto.AiDtos.ProductRecs;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * FR-AI-3.1: products to offer a customer next, from the tenant's purchase history (accepted
 * quotations and won deals, last three years):
 * <ul>
 *   <li>bought together — other customers who bought what this customer has also bought it;</li>
 *   <li>next purchase — other customers bought it after what this customer has;</li>
 *   <li>re-order — this customer buys it regularly and is due again;</li>
 *   <li>popular in their industry — when there's little history to go on.</li>
 * </ul>
 * Products already bought (except re-orders) and inactive catalogue products are left out. Each
 * suggestion says why. Computed from CRM data — no language model.
 */
@Service
@RequiredArgsConstructor
public class ProductRecommendationService {

    private static final int MAX = 6;
    private static final int MIN_SUPPORT = 2;
    private static final int HISTORY_YEARS = 3;

    private final JdbcTemplate jdbc;
    private final CustomerService customerService;

    /** One purchase: who, what (normalised name + as written), when. */
    private record Purchase(long customerId, String key, String name, LocalDate on, String industry) {}

    @Transactional(readOnly = true)
    public ProductRecs recommend(User user, Long customerId) {
        CustomerResDto customer = customerService.getCustomer(user, customerId); // scope check
        Long tenantId = user.getTenant().getId();
        List<Purchase> purchases = purchases(tenantId);
        Map<String, BigDecimal> catalog = new HashMap<>();
        Set<String> inactive = new HashSet<>();
        jdbc.query("SELECT name, unit_price, active FROM products WHERE tenant_id = ?", rs -> {
            String key = key(rs.getString("name"));
            if (rs.getBoolean("active")) {
                catalog.put(key, rs.getBigDecimal("unit_price"));
            } else {
                inactive.add(key);
            }
        }, tenantId);

        Map<String, String> names = new HashMap<>();
        Map<String, Set<Long>> buyers = new HashMap<>();
        Map<Long, Map<String, List<LocalDate>>> byCustomer = new HashMap<>();
        for (Purchase p : purchases) {
            names.putIfAbsent(p.key(), p.name());
            buyers.computeIfAbsent(p.key(), k -> new HashSet<>()).add(p.customerId());
            byCustomer.computeIfAbsent(p.customerId(), k -> new HashMap<>()).computeIfAbsent(p.key(), k -> new ArrayList<>()).add(p.on());
        }
        byCustomer.values().forEach(m -> m.values().forEach(Collections::sort));
        Map<String, List<LocalDate>> mine = byCustomer.getOrDefault(customerId, Map.of());
        long me = customerId;
        LocalDate today = LocalDate.now();

        Map<String, ProductRec> recs = new LinkedHashMap<>();
        // Bought together / bought next.
        for (String candidate : buyers.keySet()) {
            if (mine.containsKey(candidate) || inactive.contains(candidate)) {
                continue;
            }
            ProductRec best = null;
            for (String owned : mine.keySet()) {
                Set<Long> ownedBuyers = buyers.get(owned);
                long both = ownedBuyers.stream().filter(c -> c != me && buyers.get(candidate).contains(c)).count();
                long others = ownedBuyers.stream().filter(c -> c != me).count();
                if (both < MIN_SUPPORT || others == 0) {
                    continue;
                }
                long after = ownedBuyers.stream().filter(c -> c != me && buyers.get(candidate).contains(c))
                        .filter(c -> byCustomer.get(c).get(candidate).get(0).isAfter(byCustomer.get(c).get(owned).get(0))).count();
                double together = (double) both / others;
                boolean next = after * 2 > both; // most bought it later, not with it
                ProductRec rec = new ProductRec(names.get(candidate), catalog.get(candidate), next ? "next_purchase" : "bought_together",
                        round2(together),
                        next ? after + " of " + others + " customers who bought " + names.get(owned) + " went on to buy it"
                                : Math.round(together * 100) + "% of customers who bought " + names.get(owned) + " also bought it (" + both + ")");
                if (best == null || rec.score() > best.score()) {
                    best = rec;
                }
            }
            if (best != null) {
                recs.put(candidate, best);
            }
        }

        // Due for a re-order: bought at least twice, and the usual gap has passed.
        for (Map.Entry<String, List<LocalDate>> owned : mine.entrySet()) {
            if (inactive.contains(owned.getKey())) {
                continue;
            }
            Long gap = typicalGap(owned.getKey(), owned.getValue(), byCustomer);
            if (gap == null) {
                continue;
            }
            LocalDate last = owned.getValue().get(owned.getValue().size() - 1);
            long since = ChronoUnit.DAYS.between(last, today);
            if (since >= gap) {
                recs.put(owned.getKey(), new ProductRec(names.get(owned.getKey()), catalog.get(owned.getKey()), "reorder",
                        round2(Math.min(1, 0.6 + 0.4 * Math.min(1, (double) (since - gap) / gap))),
                        "Usually re-ordered every ~" + gap + " days; last bought " + since + " days ago"));
            }
        }

        // Little history: what customers of the same industry buy.
        if (recs.size() < 3) {
            Map<String, Set<Long>> sameIndustry = new HashMap<>();
            for (Purchase p : purchases) {
                if (p.customerId() != me && Objects.equals(p.industry(), customer.getIndustry())) {
                    sameIndustry.computeIfAbsent(p.key(), k -> new HashSet<>()).add(p.customerId());
                }
            }
            sameIndustry.entrySet().stream()
                    .filter(e -> !mine.containsKey(e.getKey()) && !recs.containsKey(e.getKey()) && !inactive.contains(e.getKey()))
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .limit(3)
                    .forEach(e -> recs.put(e.getKey(), new ProductRec(names.get(e.getKey()), catalog.get(e.getKey()), "popular_in_industry",
                            round2(Math.min(0.5, 0.1 * e.getValue().size())),
                            "Bought by " + e.getValue().size() + " other " + customer.getIndustry() + " customer"
                                    + (e.getValue().size() == 1 ? "" : "s"))));
        }

        List<ProductRec> ranked = recs.values().stream().sorted(Comparator.comparingDouble(ProductRec::score).reversed()).limit(MAX).toList();
        List<String> purchased = mine.keySet().stream().map(names::get).sorted().toList();
        return new ProductRecs(customerId, purchased, ranked);
    }

    /** Days between purchases: this customer's own median gap, else other repeat buyers' median; null if nobody repeats it. */
    private static Long typicalGap(String product, List<LocalDate> own, Map<Long, Map<String, List<LocalDate>>> byCustomer) {
        List<Long> gaps = gaps(own);
        if (gaps.isEmpty()) {
            byCustomer.values().forEach(m -> gaps.addAll(gaps(m.getOrDefault(product, List.of()))));
            if (own.size() < 2 && gaps.size() < MIN_SUPPORT) {
                return null;
            }
        }
        if (gaps.isEmpty()) {
            return null;
        }
        Collections.sort(gaps);
        return Math.max(1, gaps.get(gaps.size() / 2));
    }

    private static List<Long> gaps(List<LocalDate> dates) {
        List<Long> out = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            long d = ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
            if (d > 0) {
                out.add(d);
            }
        }
        return out;
    }

    private List<Purchase> purchases(Long tenantId) {
        Timestamp since = Timestamp.valueOf(LocalDateTime.now().minusYears(HISTORY_YEARS));
        return jdbc.query("""
                SELECT q.customer_id, i.product_name AS name, CAST(coalesce(q.customer_approved_at, q.created_at) AS DATE) AS on_date, c.industry
                FROM quotation_line_items i JOIN quotations q ON q.id = i.quotation_id JOIN customers c ON c.id = q.customer_id
                WHERE q.tenant_id = ? AND q.status = 'approved' AND coalesce(q.customer_approved_at, q.created_at) >= ?
                UNION ALL
                SELECT o.customer_id, o.product_service, CAST(o.stage_changed_at AS DATE), c.industry
                FROM opportunities o JOIN customers c ON c.id = o.customer_id
                WHERE o.tenant_id = ? AND o.stage = 'won' AND o.product_service IS NOT NULL AND trim(o.product_service) <> ''
                  AND o.stage_changed_at >= ?""",
                (rs, i) -> new Purchase(rs.getLong("customer_id"), key(rs.getString("name")), rs.getString("name").trim(),
                        rs.getDate("on_date").toLocalDate(), rs.getString("industry")),
                tenantId, since, tenantId, since);
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
