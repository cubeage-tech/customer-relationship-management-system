package com.company.crm.ai.service;

import com.company.crm.ai.service.SalesCoachingService.FocusArea;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FR-AI-8.3: coaching seen by both the salesperson and their manager. A coaching plan (FR-AI-8.2), as
 * the sharer saw and possibly edited it, is shared with a note: by a manager, admin or the owner with
 * the salesperson, or by the salesperson with their team's manager(s). The other side is notified;
 * the salesperson acknowledges it with a reply, and the sharer is told. The history is visible to the
 * salesperson and to whoever's sales force scope covers them (their manager, admins, the owner).
 */
@Service
@RequiredArgsConstructor
public class CoachingShareService {

    private static final Set<RoleType> SALES_ROLES = Set.of(RoleType.SALES_EXECUTIVE, RoleType.SALES_MANAGER);
    private static final String PATH = "/sales-force?tab=performance";

    /** The plan as shown (it may have been edited), plus the sharer's note. */
    public record ShareReq(Long userId, LocalDate from, LocalDate to, String summary, List<FocusArea> focusAreas, List<String> strengthsToKeep,
                           List<String> weeklyGoals, String source, String note) {}

    public record StoredPlan(String summary, List<FocusArea> focusAreas, List<String> strengthsToKeep, List<String> weeklyGoals) {}

    public record SharedCoaching(Long id, Long userId, String salespersonName, Long sharedById, String sharedByName, LocalDate from, LocalDate to,
                                 StoredPlan plan, String source, String note, LocalDateTime sharedAt, LocalDateTime acknowledgedAt,
                                 String reply) {}

    private final JdbcTemplate jdbc;
    private final SalesForceScope scope;
    private final UserRepository userRepository;
    private final StaffNotifier staffNotifier;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SharedCoaching share(User user, ShareReq req) {
        Long who = req.userId() != null ? req.userId() : user.getId();
        if (!scope.canSee(user, who)) {
            throw ApiException.forbidden("You can only share coaching for yourself or your team");
        }
        User person = userRepository.findById(who).filter(u -> u.getTenant() != null && u.getTenant().getId().equals(user.getTenant().getId()))
                .filter(u -> SALES_ROLES.contains(u.getRole().getName()))
                .orElseThrow(() -> ApiException.badRequest("Coaching is for sales executives and managers"));
        if (req.from() == null || req.to() == null || req.to().isBefore(req.from())) {
            throw ApiException.badRequest("Give the coaching period");
        }
        if (req.summary() == null || req.summary().isBlank()) {
            throw ApiException.badRequest("The coaching plan is empty");
        }
        if (!"ai".equals(req.source()) && !"rules".equals(req.source())) {
            throw ApiException.badRequest("Source must be ai or rules");
        }
        String note = text(req.note(), 1000);
        StoredPlan plan = new StoredPlan(text(req.summary(), 2000), areas(req.focusAreas()), items(req.strengthsToKeep(), 8),
                items(req.weeklyGoals(), 5));
        String json;
        try {
            json = objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the coaching plan", e);
        }
        Long id = jdbc.queryForObject("INSERT INTO coaching_plans (tenant_id, salesperson_id, shared_by, period_from, period_to, plan, source, note) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                user.getTenant().getId(), person.getId(), user.getId(), Date.valueOf(req.from()), Date.valueOf(req.to()), json, req.source(), note);
        boolean own = person.getId().equals(user.getId());
        List<User> told = own ? managersOf(person) : List.of(person);
        staffNotifier.notify(user.getTenant().getId(), NotificationEvent.COACHING_SHARED, told,
                own ? person.getFullName() + " shared their coaching plan" : "Coaching from " + user.getFullName(),
                (note != null ? note + " — " : "") + plan.summary(), null, null, user.getId(), PATH);
        auditService.record(user, AuditAction.COACHING_SHARED, "user", person.getId(),
                "coaching #" + id + " for " + req.from() + "–" + req.to() + (own ? ", shared with their manager" : ""));
        return get(user, id);
    }

    @Transactional(readOnly = true)
    public List<SharedCoaching> history(User user, Long userId) {
        Long who = userId != null ? userId : user.getId();
        if (!scope.canSee(user, who)) {
            throw ApiException.forbidden("You can only see coaching for yourself or your team");
        }
        return jdbc.query(SELECT + " WHERE c.tenant_id = ? AND c.salesperson_id = ? ORDER BY c.created_at DESC, c.id DESC LIMIT 20", this::map,
                user.getTenant().getId(), who);
    }

    /** The salesperson confirms they have read it, optionally replying; the sharer is told. */
    @Transactional
    public SharedCoaching acknowledge(User user, Long id, String reply) {
        SharedCoaching c = get(user, id);
        if (!c.userId().equals(user.getId())) {
            throw ApiException.forbidden("Only the salesperson coached can acknowledge it");
        }
        if (c.acknowledgedAt() != null) {
            throw ApiException.conflict("Already acknowledged");
        }
        String r = text(reply, 1000);
        jdbc.update("UPDATE coaching_plans SET acknowledged_at = now(), reply = ? WHERE id = ?", r, id);
        if (c.sharedById() != null && !c.sharedById().equals(user.getId())) {
            userRepository.findById(c.sharedById()).filter(u -> u.getStatus() == AccountStatus.ACTIVE).ifPresent(sharer ->
                    staffNotifier.notify(user.getTenant().getId(), NotificationEvent.COACHING_SHARED, List.of(sharer),
                            user.getFullName() + " acknowledged your coaching", r != null ? r : "Read and acknowledged.", null, null, user.getId(), PATH));
        }
        auditService.record(user, AuditAction.COACHING_ACKNOWLEDGED, "user", user.getId(), "coaching #" + id);
        return get(user, id);
    }

    // ==================== helpers ====================

    private static final String SELECT = "SELECT c.id, c.salesperson_id, p.full_name AS person, c.shared_by, s.full_name AS sharer, c.period_from, "
            + "c.period_to, c.plan, c.source, c.note, c.created_at, c.acknowledged_at, c.reply FROM coaching_plans c "
            + "JOIN users p ON p.id = c.salesperson_id LEFT JOIN users s ON s.id = c.shared_by";

    private SharedCoaching get(User user, Long id) {
        List<SharedCoaching> rows = jdbc.query(SELECT + " WHERE c.id = ? AND c.tenant_id = ?", this::map, id, user.getTenant().getId());
        if (rows.isEmpty() || !scope.canSee(user, rows.get(0).userId())) {
            throw ApiException.notFound("Coaching not found");
        }
        return rows.get(0);
    }

    /** The manager(s) of the person's sales team; admins when there is none. */
    private List<User> managersOf(User person) {
        List<Long> ids = jdbc.queryForList("SELECT t.manager_id FROM users u JOIN sales_teams t ON t.id = u.team_id WHERE u.id = ? "
                + "AND t.manager_id IS NOT NULL AND t.manager_id <> u.id", Long.class, person.getId());
        List<User> managers = userRepository.findAllById(ids).stream().filter(u -> u.getStatus() == AccountStatus.ACTIVE).toList();
        return !managers.isEmpty() ? managers : userRepository.findByTenantId(person.getTenant().getId()).stream()
                .filter(u -> u.getRole().getName() == RoleType.ADMIN && u.getStatus() == AccountStatus.ACTIVE).toList();
    }

    private SharedCoaching map(ResultSet rs, int i) throws SQLException {
        StoredPlan plan;
        try {
            plan = objectMapper.readValue(rs.getString("plan"), StoredPlan.class);
        } catch (JsonProcessingException e) {
            plan = new StoredPlan("", List.of(), List.of(), List.of());
        }
        Timestamp ack = rs.getTimestamp("acknowledged_at");
        return new SharedCoaching(rs.getLong("id"), rs.getLong("salesperson_id"), rs.getString("person"), (Long) rs.getObject("shared_by"),
                rs.getString("sharer"), rs.getDate("period_from").toLocalDate(), rs.getDate("period_to").toLocalDate(), plan, rs.getString("source"),
                rs.getString("note"), rs.getTimestamp("created_at").toLocalDateTime(), ack == null ? null : ack.toLocalDateTime(), rs.getString("reply"));
    }

    private static List<FocusArea> areas(List<FocusArea> areas) {
        List<FocusArea> out = new ArrayList<>();
        if (areas != null) {
            for (FocusArea a : areas) {
                if (a != null && a.area() != null && !a.area().isBlank() && out.size() < 5) {
                    out.add(new FocusArea(text(a.area(), 120), text(a.why(), 500), items(a.actions(), 5), text(a.measure(), 300)));
                }
            }
        }
        return out;
    }

    private static List<String> items(List<String> items, int max) {
        return items == null ? List.of() : items.stream().filter(s -> s != null && !s.isBlank()).map(s -> text(s, 300)).limit(max).toList();
    }

    private static String text(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
