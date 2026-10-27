package com.company.crm.notification.delivery;

import com.company.crm.common.enums.CommunicationStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.communication.entity.Communication;
import com.company.crm.customer.entity.Customer;
import com.company.crm.lead.entity.Lead;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NOT-8: one row per notification per channel — who it went to, through what, and how it went
 * (sent, delivered, read, failed or skipped, with the reason). A message to a customer links to its
 * communication log row, so the provider's later delivery reports show here too; an in-app
 * notification shows as read once the user opens it.
 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryLog {

    public static final String QUEUED = "queued";
    public static final String SENT = "sent";
    public static final String FAILED = "failed";
    public static final String SKIPPED = "skipped";

    private static final int MAX_DETAIL = 300;
    private static final int PAGE = 100;

    private final JdbcTemplate jdbc;

    /** Who a notification went to. */
    public record Recipient(String type, Long id, String name) {
        public static Recipient user(User u) {
            return new Recipient("user", u.getId(), u.getFullName());
        }

        public static Recipient customer(Customer c) {
            return new Recipient("customer", c.getId(), c.getCompanyName());
        }

        public static Recipient lead(Lead l) {
            return new Recipient("lead", l.getId(), l.getLeadName());
        }
    }

    public record DeliveryRes(Long id, String event, String eventLabel, String channel, String recipientType, Long recipientId,
                              String recipientName, String status, String detail, Long communicationId, LocalDateTime createdAt) {}

    @Transactional
    public Long log(Long tenantId, NotificationEvent event, String channel, Recipient to, Long notificationId,
                    Long communicationId, String status, String detail) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO notification_deliveries (tenant_id, event, channel, recipient_type,"
                    + " recipient_id, recipient_name, notification_id, communication_id, status, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, tenantId);
            ps.setString(2, event.getCode());
            ps.setString(3, channel);
            ps.setString(4, to.type());
            ps.setObject(5, to.id());
            ps.setString(6, truncate(to.name(), 200));
            ps.setObject(7, notificationId);
            ps.setObject(8, communicationId);
            ps.setString(9, status);
            ps.setString(10, truncate(detail, MAX_DETAIL));
            return ps;
        }, key);
        Number id = (Number) key.getKeys().get("id");
        return id == null ? null : id.longValue();
    }

    /** A message to a customer or lead, logged with its communication row (whose status keeps updating). */
    @Transactional
    public void logMessage(Long tenantId, NotificationEvent event, String channel, Recipient to, Communication message) {
        if (message == null) {
            return;
        }
        String status = switch (message.getStatus()) {
            case FAILED -> FAILED;
            case QUEUED -> QUEUED;
            default -> SENT;
        };
        log(tenantId, event, channel, to, null, message.getId(), status,
                message.getStatus() == CommunicationStatus.FAILED || message.getStatus() == CommunicationStatus.QUEUED ? message.getErrorMessage() : null);
    }

    @Transactional
    public void skipped(Long tenantId, NotificationEvent event, String channel, Recipient to, String why) {
        log(tenantId, event, channel, to, null, null, SKIPPED, why);
    }

    /**
     * The tenant's log, newest first (a page of 100). Statuses are current: a customer message's
     * comes from its communication log, an in-app notification reads "read" once opened.
     */
    @Transactional(readOnly = true)
    public List<DeliveryRes> list(User admin, String channel, String status, String event, LocalDate from, LocalDate to, int page) {
        if (admin.getTenant() == null) {
            throw ApiException.forbidden("The notification log is per tenant");
        }
        String effective = "CASE WHEN d.communication_id IS NOT NULL THEN"
                + " (CASE WHEN c.status IN ('failed') THEN 'failed' WHEN c.status IN ('queued') THEN 'queued'"
                + " WHEN c.status IN ('delivered', 'read') THEN c.status ELSE 'sent' END)"
                + " WHEN d.channel = 'in_app' AND n.read_at IS NOT NULL THEN 'read' ELSE d.status END";
        StringBuilder sql = new StringBuilder("SELECT d.id, d.event, d.channel, d.recipient_type, d.recipient_id, d.recipient_name, "
                + effective + " AS eff_status, COALESCE(CASE WHEN c.status = 'failed' THEN c.error_message END, d.detail) AS eff_detail,"
                + " d.communication_id, d.created_at FROM notification_deliveries d"
                + " LEFT JOIN communication_logs c ON c.id = d.communication_id"
                + " LEFT JOIN notifications n ON n.id = d.notification_id WHERE d.tenant_id = ?");
        List<Object> args = new ArrayList<>(List.of(admin.getTenant().getId()));
        if (channel != null && !channel.isBlank()) {
            sql.append(" AND d.channel = ?");
            args.add(channel);
        }
        if (event != null && !event.isBlank()) {
            sql.append(" AND d.event = ?");
            args.add(event);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND ").append(effective).append(" = ?");
            args.add(status);
        }
        if (from != null) {
            sql.append(" AND d.created_at >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND d.created_at < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY d.created_at DESC, d.id DESC LIMIT ").append(PAGE).append(" OFFSET ").append(Math.max(0, page) * PAGE);
        return jdbc.query(sql.toString(), (rs, i) -> {
            String code = rs.getString("event");
            return new DeliveryRes(rs.getLong("id"), code, NotificationEvent.fromCode(code).map(NotificationEvent::getLabel).orElse(code),
                    rs.getString("channel"), rs.getString("recipient_type"), (Long) rs.getObject("recipient_id", Long.class),
                    rs.getString("recipient_name"), rs.getString("eff_status"), rs.getString("eff_detail"),
                    (Long) rs.getObject("communication_id", Long.class), rs.getTimestamp("created_at").toLocalDateTime());
        }, args.toArray());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
