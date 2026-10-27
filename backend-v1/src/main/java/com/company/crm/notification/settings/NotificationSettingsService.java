package com.company.crm.notification.settings;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.communication.dto.request.SmsAlertRuleReqDto;
import com.company.crm.communication.dto.responce.SmsAlertRuleResDto;
import com.company.crm.communication.integration.TwilioClient;
import com.company.crm.communication.service.SmsAlertService;
import com.company.crm.integration.health.IntegrationHealthService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * NOT-6: which events notify, through which channels, per tenant. A tenant admin switches each
 * event's channels on or off; until then an event uses its defaults. SMS is the FR-5.3 SMS alert
 * rule of the event (with its recipient); the other channels live in notification_settings.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final JdbcTemplate jdbc;
    private final SmsAlertService smsAlertService;
    private final TwilioClient twilioClient;
    private final IntegrationHealthService integrationHealth;
    private final AuditService auditService;

    public record ChannelSetting(String channel, boolean enabled, boolean available, String note, String recipient) {}

    public record EventSettings(String event, String label, String audience, List<ChannelSetting> channels) {}

    /** One change: an event's channel on/off (and, for SMS, who receives it: customer | internal). */
    public record ChannelUpdate(String event, String channel, boolean enabled, String recipient) {}

    /** Whether the event goes out on the channel in this tenant. */
    @Transactional(readOnly = true)
    public boolean isEnabled(Long tenantId, NotificationEvent event, NotificationChannel channel) {
        if (!event.getChannels().contains(channel)) {
            return false;
        }
        if (!event.isConfigurable() || tenantId == null) {
            return event.getDefaults().contains(channel);
        }
        if (channel == NotificationChannel.SMS) {
            return smsAlertService.isEnabled(tenantId, event.getSmsEvent());
        }
        List<Boolean> saved = jdbc.queryForList("SELECT enabled FROM notification_settings WHERE tenant_id = ? AND event = ? AND channel = ?",
                Boolean.class, tenantId, event.getCode(), channel.getCode());
        return saved.isEmpty() ? event.getDefaults().contains(channel) : saved.get(0);
    }

    @Transactional(readOnly = true)
    public List<EventSettings> list(User admin) {
        Long tenantId = tenantId(admin);
        Map<String, SmsAlertRuleResDto> sms = smsAlertService.listRules(admin).stream()
                .collect(Collectors.toMap(SmsAlertRuleResDto::event, r -> r));
        String twilioNote = integrationHealth.problem(IntegrationHealthService.TWILIO).orElse(null);
        return Arrays.stream(NotificationEvent.values()).filter(NotificationEvent::isConfigurable).map(event -> {
            List<ChannelSetting> channels = new ArrayList<>();
            for (NotificationChannel ch : NotificationChannel.values()) {
                if (!event.getChannels().contains(ch)) {
                    continue;
                }
                switch (ch) {
                    case SMS -> {
                        SmsAlertRuleResDto rule = sms.get(event.getSmsEvent().getDbValue());
                        channels.add(new ChannelSetting(ch.getCode(), rule != null && rule.enabled(), twilioClient.isSmsEnabled(),
                                twilioClient.isSmsEnabled() ? twilioNote : "SMS isn't set up for this CRM", rule != null ? rule.recipient() : null));
                    }
                    case WHATSAPP -> channels.add(new ChannelSetting(ch.getCode(), isEnabled(tenantId, event, ch), twilioClient.isWhatsAppEnabled(),
                            twilioClient.isWhatsAppEnabled() ? Optional.ofNullable(twilioNote).orElse("Only to customers who opted in to WhatsApp")
                                    : "WhatsApp isn't set up for this CRM", null));
                    default -> channels.add(new ChannelSetting(ch.getCode(), isEnabled(tenantId, event, ch), true, null, null));
                }
            }
            return new EventSettings(event.getCode(), event.getLabel(), event.getAudience().name().toLowerCase(), channels);
        }).toList();
    }

    @Transactional
    public List<EventSettings> update(User admin, List<ChannelUpdate> changes) {
        Long tenantId = tenantId(admin);
        List<SmsAlertRuleReqDto> smsRules = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        for (ChannelUpdate change : changes == null ? List.<ChannelUpdate>of() : changes) {
            NotificationEvent event = NotificationEvent.fromCode(change.event())
                    .filter(NotificationEvent::isConfigurable)
                    .orElseThrow(() -> ApiException.badRequest("Unknown notification event: " + change.event()));
            NotificationChannel channel;
            try {
                channel = NotificationChannel.fromCode(change.channel());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown channel: " + change.channel());
            }
            if (!event.getChannels().contains(channel)) {
                throw ApiException.badRequest(event.getLabel() + " can't be sent by " + channel.getCode());
            }
            if (channel == NotificationChannel.SMS) {
                SmsAlertRuleReqDto rule = new SmsAlertRuleReqDto();
                rule.setEvent(event.getSmsEvent().getDbValue());
                rule.setEnabled(change.enabled());
                rule.setRecipient(change.recipient() != null ? change.recipient()
                        : event.getAudience() == NotificationEvent.Audience.CUSTOMER ? "customer" : "internal");
                smsRules.add(rule);
            } else {
                jdbc.update("INSERT INTO notification_settings (tenant_id, event, channel, enabled, updated_by, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, now()) ON CONFLICT (tenant_id, event, channel)"
                        + " DO UPDATE SET enabled = EXCLUDED.enabled, updated_by = EXCLUDED.updated_by, updated_at = now()",
                        tenantId, event.getCode(), channel.getCode(), change.enabled(), admin.getId());
            }
            summary.add(event.getCode() + "." + channel.getCode() + "=" + (change.enabled() ? "on" : "off"));
        }
        if (!smsRules.isEmpty()) {
            smsAlertService.updateRules(admin, smsRules);
        }
        if (!summary.isEmpty()) {
            auditService.record(admin, AuditAction.NOTIFICATION_SETTINGS_CHANGED, "notification_settings", null, String.join(", ", summary));
        }
        return list(admin);
    }

    private static Long tenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Notification settings are per tenant");
        }
        return user.getTenant().getId();
    }
}
