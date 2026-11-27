package com.company.crm.notification.service;

import com.company.crm.analytics.dto.AnalyticsDtos.Headline;
import com.company.crm.analytics.service.AnalyticsService;
import com.company.crm.analytics.service.ReportScopeResolver;
import com.company.crm.common.mail.MailService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NOT-1: the weekly digest email. Each active user who hasn't opted out gets last week's figures
 * from their own data scope (leads, conversions, deals won, open pipeline) and their week ahead
 * (follow-ups due, meetings, open tickets). At most one digest per user per week.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestService {

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;
    private final ReportScopeResolver scopeResolver;
    private final MailService mailService;
    private final JdbcTemplate jdbc;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public record Digest(String subject, String body) {}

    @Transactional(readOnly = true)
    public List<Long> recipients() {
        return userRepository.findWeeklyDigestRecipientIds();
    }

    /** Sends one user's digest in its own transaction; false when already sent this week or the user has none. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean send(Long userId) {
        if (jdbc.update("UPDATE users SET last_digest_at = now() WHERE id = ? AND weekly_digest"
                + " AND (last_digest_at IS NULL OR last_digest_at < now() - interval '6 days')", userId) == 0) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getTenant() == null || user.getEmail() == null) {
            return false;
        }
        Digest digest = compose(user, LocalDate.now());
        try {
            mailService.sendCalendarEmail(user.getEmail(), digest.subject(), digest.body(), null);
        } catch (Exception e) {
            log.warn("Weekly digest for user {} failed: {}", userId, e.getMessage());
        }
        return true;
    }

    /** The digest for the seven days before {@code today}, and the seven days from it. */
    public Digest compose(User user, LocalDate today) {
        Headline h = analyticsService.headline(user, scopeResolver.resolve(user, today.minusDays(7), today.minusDays(1), null, null, null));
        Long tenantId = user.getTenant().getId();
        long followUps = count("SELECT COUNT(*) FROM leads WHERE tenant_id = ? AND owner_id = ? AND stage <> 'converted'"
                + " AND follow_up_date >= now() AND follow_up_date < now() + interval '7 days'", tenantId, user.getId());
        long meetings = count("SELECT COUNT(*) FROM meetings WHERE tenant_id = ? AND organizer_id = ? AND status = 'scheduled'"
                + " AND starts_at >= now() AND starts_at < now() + interval '7 days'", tenantId, user.getId());
        long tickets = count("SELECT COUNT(*) FROM service_tickets WHERE tenant_id = ? AND assigned_technician_id = ?"
                + " AND status IN ('open', 'assigned', 'in_progress')", tenantId, user.getId());

        List<String> ahead = new ArrayList<>();
        if (followUps > 0) ahead.add("- " + followUps + " lead follow-up(s) due");
        if (meetings > 0) ahead.add("- " + meetings + " meeting(s) scheduled");
        if (tickets > 0) ahead.add("- " + tickets + " open service ticket(s) assigned to you");

        String company = user.getTenant().getCompanyName();
        String body = "Hi " + user.getFullName() + ",\n\n"
                + "Here is your week in " + company + " (" + h.from() + " to " + h.to() + "):\n\n"
                + "- New leads: " + h.current().totalLeads() + " (previous week " + h.previous().totalLeads() + ")\n"
                + "- Leads converted: " + h.current().convertedLeads() + " (" + h.current().conversionRate() + "%)\n"
                + "- Deals won: " + h.current().wonDeals() + ", worth " + money(h.current().revenue()) + "\n"
                + "- Open pipeline: " + h.openDeals() + " deal(s), worth " + money(h.openPipelineValue()) + "\n\n"
                + (ahead.isEmpty() ? "Nothing is due for you in the next 7 days.\n" : "In the next 7 days:\n" + String.join("\n", ahead) + "\n")
                + "\nOpen your dashboard: " + frontendUrl + "\n\n"
                + "You get this email every Monday. To stop it, switch off the weekly digest in your notification settings.";
        String subject = "Your week in " + company + ": " + h.current().totalLeads() + " new lead(s), "
                + h.current().wonDeals() + " deal(s) won";
        return new Digest(subject, body);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static String money(BigDecimal v) {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).format(v == null ? BigDecimal.ZERO : v);
    }

    // ==================== preference ====================

    @Transactional(readOnly = true)
    public boolean isSubscribed(User user) {
        return userRepository.findById(user.getId()).map(User::isWeeklyDigest).orElse(false);
    }

    @Transactional
    public boolean setSubscribed(User user, boolean subscribed) {
        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setWeeklyDigest(subscribed);
        userRepository.save(u);
        return subscribed;
    }
}
