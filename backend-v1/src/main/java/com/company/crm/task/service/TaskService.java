package com.company.crm.task.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.lead.service.LeadService;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.meeting.service.MeetingService;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.sales.dto.response.OpportunityResDto;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FR-AI-7.3: follow-up tasks for a customer, lead or deal — made by hand, or from a meeting's action
 * items once the user has confirmed them. A task is seen by its assignee, its creator and whoever's
 * data scope covers the assignee (managers, admins); the linked record must be visible to whoever
 * creates it. Assignees are active colleagues in the tenant. When a task falls due its assignee is
 * reminded once (in the app, and by email if the tenant chose), like lead follow-ups (FR-2.5).
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final Set<String> STATUSES = Set.of("open", "done", "cancelled");
    private static final Set<RoleType> STAFF = Set.of(RoleType.ADMIN, RoleType.SALES_MANAGER, RoleType.SALES_EXECUTIVE, RoleType.SERVICE_AGENT,
            RoleType.MARKETING_EXECUTIVE, RoleType.FINANCE_APPROVER, RoleType.EXECUTIVE_OWNER);
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d MMM yyyy");

    /** One of customerId / leadId / opportunityId links the task; assigneeId defaults to the caller. */
    public record TaskReq(String title, String notes, LocalDate dueDate, Long assigneeId, Long customerId, Long leadId, Long opportunityId) {}

    public record TaskUpdate(String title, String notes, LocalDate dueDate, Long assigneeId, String status) {}

    /** A confirmed meeting action item. */
    public record MeetingTask(String title, String notes, LocalDate dueDate, Long assigneeId) {}

    public record TaskRes(Long id, String title, String notes, LocalDate dueDate, String status, boolean overdue, Long assigneeId,
                          String assigneeName, Long customerId, String customerName, Long leadId, String leadName, Long opportunityId,
                          Long meetingId, String meetingTitle, String source, Long createdById, String createdByName, LocalDateTime createdAt,
                          LocalDateTime completedAt) {}

    public record Assignee(Long id, String name, String role) {}

    private final JdbcTemplate jdbc;
    private final DataScopeResolver scopes;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final OpportunityService opportunityService;
    private final MeetingService meetingService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StaffNotifier staffNotifier;

    // ==================== reading ====================

    /** {@code mine}: only tasks assigned to the caller; otherwise all the caller may see (optionally for one record / status). */
    @Transactional(readOnly = true)
    public List<TaskRes> list(User user, boolean mine, Long customerId, Long leadId, Long opportunityId, String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw ApiException.badRequest("Status must be open, done or cancelled");
        }
        StringBuilder where = new StringBuilder(" WHERE t.tenant_id = ?");
        List<Object> args = new ArrayList<>(List.of(user.getTenant().getId()));
        if (mine) {
            where.append(" AND t.assignee_id = ?");
            args.add(user.getId());
        }
        if (customerId != null) {
            customerService.getCustomer(user, customerId);
            where.append(" AND t.customer_id = ?");
            args.add(customerId);
        }
        if (leadId != null) {
            leadService.getLead(user, leadId);
            where.append(" AND t.lead_id = ?");
            args.add(leadId);
        }
        if (opportunityId != null) {
            opportunityService.getOpportunity(user, opportunityId);
            where.append(" AND t.opportunity_id = ?");
            args.add(opportunityId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ?");
            args.add(status);
        }
        DataScope scope = scopes.resolve(user);
        return jdbc.query(SELECT + where + " ORDER BY t.status = 'open' DESC, t.due_date NULLS LAST, t.id", this::map, args.toArray()).stream()
                .filter(t -> visible(user, scope, t)).toList();
    }

    /** Colleagues a task can be given to. */
    @Transactional(readOnly = true)
    public List<Assignee> assignees(User user) {
        return userRepository.findByTenantId(user.getTenant().getId()).stream()
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE && STAFF.contains(u.getRole().getName()))
                .map(u -> new Assignee(u.getId(), u.getFullName(), u.getRole().getName().name().toLowerCase()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    // ==================== changing ====================

    @Transactional
    public TaskRes create(User user, TaskReq req) {
        int links = (req.customerId() != null ? 1 : 0) + (req.leadId() != null ? 1 : 0) + (req.opportunityId() != null ? 1 : 0);
        if (links != 1) {
            throw ApiException.badRequest("Link the task to one customer, lead or deal");
        }
        Long customerId = req.customerId();
        if (customerId != null) {
            customerService.getCustomer(user, customerId);
        }
        if (req.leadId() != null) {
            leadService.getLead(user, req.leadId());
        }
        if (req.opportunityId() != null) {
            OpportunityResDto deal = opportunityService.getOpportunity(user, req.opportunityId());
            customerId = deal.getCustomerId(); // shown on the customer too
        }
        Long id = insert(user, title(req.title()), notes(req.notes()), req.dueDate(), assignee(user, req.assigneeId()), customerId, req.leadId(),
                req.opportunityId(), null, "manual");
        return get(user, id);
    }

    /** Confirmed action items of a meeting become tasks on its customer or lead. */
    @Transactional
    public List<TaskRes> createFromMeeting(User user, Long meetingId, List<MeetingTask> items) {
        Meeting meeting = meetingService.requireAccessible(user, meetingId);
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("Choose at least one action item");
        }
        if (items.size() > 20) {
            throw ApiException.badRequest("At most 20 tasks at once");
        }
        Long customerId = meeting.getCustomer() != null ? meeting.getCustomer().getId() : null;
        Long leadId = meeting.getLead() != null ? meeting.getLead().getId() : null;
        List<Long> ids = new ArrayList<>();
        for (MeetingTask item : items) {
            ids.add(insert(user, title(item.title()), notes(item.notes()), item.dueDate(), assignee(user, item.assigneeId()), customerId, leadId,
                    null, meeting.getId(), "meeting_ai"));
        }
        auditService.record(user, AuditAction.FOLLOW_UP_TASKS_CREATED, "meeting", meeting.getId(),
                ids.size() + " task(s) from the meeting's action items");
        return ids.stream().map(id -> get(user, id)).toList();
    }

    @Transactional
    public TaskRes update(User user, Long taskId, TaskUpdate req) {
        TaskRes task = get(user, taskId);
        String status = req.status() != null ? req.status() : task.status();
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("Status must be open, done or cancelled");
        }
        Long assigneeId = req.assigneeId() != null ? assignee(user, req.assigneeId()) : task.assigneeId();
        LocalDate due = req.dueDate() != null ? req.dueDate() : task.dueDate();
        boolean dueMoved = req.dueDate() != null && !req.dueDate().equals(task.dueDate());
        jdbc.update("UPDATE follow_up_tasks SET title = ?, notes = ?, due_date = ?, assignee_id = ?, status = ?, "
                        + "completed_at = CASE WHEN ? = 'done' THEN coalesce(completed_at, now()) ELSE NULL END, "
                        + "due_notified_at = CASE WHEN ? THEN NULL ELSE due_notified_at END, updated_at = now() WHERE id = ?",
                req.title() != null ? title(req.title()) : task.title(), req.notes() != null ? notes(req.notes()) : task.notes(),
                due == null ? null : Date.valueOf(due), assigneeId, status, status, dueMoved || !assigneeId.equals(task.assigneeId()), taskId);
        return get(user, taskId);
    }

    // ==================== reminders ====================

    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return jdbc.queryForList("SELECT id FROM follow_up_tasks WHERE status = 'open' AND due_notified_at IS NULL AND due_date <= current_date",
                Long.class);
    }

    /** One task's reminder in its own transaction; claimed first so it is sent once. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remind(Long taskId) {
        if (jdbc.update("UPDATE follow_up_tasks SET due_notified_at = now() WHERE id = ? AND due_notified_at IS NULL AND status = 'open'", taskId) == 0) {
            return;
        }
        jdbc.query("SELECT t.tenant_id, t.title, t.due_date, t.assignee_id, t.customer_id, t.lead_id, t.opportunity_id, "
                + "coalesce(c.company_name, l.lead_name) AS about FROM follow_up_tasks t LEFT JOIN customers c ON c.id = t.customer_id "
                + "LEFT JOIN leads l ON l.id = t.lead_id WHERE t.id = ?", rs -> {
            User assignee = userRepository.findById(rs.getLong("assignee_id")).filter(u -> u.getStatus() == AccountStatus.ACTIVE).orElse(null);
            if (assignee == null) {
                return;
            }
            Long customerId = (Long) rs.getObject("customer_id");
            Long leadId = (Long) rs.getObject("lead_id");
            String entity = customerId != null ? "customer" : leadId != null ? "lead" : null;
            Long entityId = customerId != null ? customerId : leadId;
            LocalDate due = rs.getDate("due_date").toLocalDate();
            staffNotifier.notify(rs.getLong("tenant_id"), NotificationEvent.FOLLOW_UP_DUE, List.of(assignee), "Task due: " + rs.getString("title"),
                    (rs.getString("about") != null ? rs.getString("about") + " · " : "") + "due " + DUE.format(due),
                    entity, entityId, null, entity != null ? "/" + entity + "s/" + entityId : null);
        }, taskId);
    }

    // ==================== helpers ====================

    private static final String SELECT = "SELECT t.id, t.title, t.notes, t.due_date, t.status, t.assignee_id, a.full_name AS assignee_name, "
            + "t.customer_id, c.company_name, t.lead_id, l.lead_name, t.opportunity_id, t.meeting_id, m.title AS meeting_title, t.source, "
            + "cb.full_name AS created_by_name, t.created_by, t.created_at, t.completed_at FROM follow_up_tasks t "
            + "JOIN users a ON a.id = t.assignee_id LEFT JOIN customers c ON c.id = t.customer_id LEFT JOIN leads l ON l.id = t.lead_id "
            + "LEFT JOIN meetings m ON m.id = t.meeting_id LEFT JOIN users cb ON cb.id = t.created_by";

    private TaskRes get(User user, Long taskId) {
        List<TaskRes> rows = jdbc.query(SELECT + " WHERE t.id = ? AND t.tenant_id = ?", this::map, taskId, user.getTenant().getId());
        if (rows.isEmpty()) {
            throw ApiException.notFound("Task not found");
        }
        TaskRes task = rows.get(0);
        if (!visible(user, scopes.resolve(user), task)) {
            throw ApiException.forbidden("You don't have access to this task");
        }
        return task;
    }

    /** Assignee, creator, or the caller's data scope covers the assignee. */
    private boolean visible(User user, DataScope scope, TaskRes t) {
        return t.assigneeId().equals(user.getId()) || user.getId().equals(t.createdById()) || scope.allows(t.assigneeId());
    }

    private Long insert(User user, String title, String notes, LocalDate due, Long assigneeId, Long customerId, Long leadId, Long opportunityId,
                        Long meetingId, String source) {
        return jdbc.queryForObject("INSERT INTO follow_up_tasks (tenant_id, title, notes, due_date, assignee_id, customer_id, lead_id, opportunity_id, "
                        + "meeting_id, source, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                user.getTenant().getId(), title, notes, due == null ? null : Date.valueOf(due), assigneeId, customerId, leadId, opportunityId,
                meetingId, source, user.getId());
    }

    private Long assignee(User user, Long assigneeId) {
        if (assigneeId == null || assigneeId.equals(user.getId())) {
            return user.getId();
        }
        return userRepository.findById(assigneeId)
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(user.getTenant().getId()))
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE && STAFF.contains(u.getRole().getName()))
                .map(User::getId)
                .orElseThrow(() -> ApiException.badRequest("Assign the task to an active colleague"));
    }

    private static String title(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > 200) {
            throw ApiException.badRequest("Give the task a title of up to 200 characters");
        }
        return t;
    }

    private static String notes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        if (notes.length() > 2000) {
            throw ApiException.badRequest("Notes can be up to 2000 characters");
        }
        return notes.trim();
    }

    private TaskRes map(ResultSet rs, int i) throws SQLException {
        Date due = rs.getDate("due_date");
        Timestamp done = rs.getTimestamp("completed_at");
        String status = rs.getString("status");
        return new TaskRes(rs.getLong("id"), rs.getString("title"), rs.getString("notes"), due == null ? null : due.toLocalDate(), status,
                "open".equals(status) && due != null && due.toLocalDate().isBefore(LocalDate.now()), rs.getLong("assignee_id"),
                rs.getString("assignee_name"), (Long) rs.getObject("customer_id"), rs.getString("company_name"), (Long) rs.getObject("lead_id"),
                rs.getString("lead_name"), (Long) rs.getObject("opportunity_id"), (Long) rs.getObject("meeting_id"), rs.getString("meeting_title"),
                rs.getString("source"), (Long) rs.getObject("created_by"), rs.getString("created_by_name"), rs.getTimestamp("created_at").toLocalDateTime(),
                done == null ? null : done.toLocalDateTime());
    }
}
