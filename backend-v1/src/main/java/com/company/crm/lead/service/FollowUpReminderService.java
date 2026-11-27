package com.company.crm.lead.service;

import com.company.crm.notification.settings.NotificationSettingsService;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.settings.NotificationChannel;
import com.company.crm.notification.delivery.NotificationDeliveryLog.Recipient;
import com.company.crm.notification.delivery.NotificationDeliveryLog;
import com.company.crm.common.mail.MailQueuedException;
import com.company.crm.common.enums.LeadStage;
import com.company.crm.common.mail.MailService;
import com.company.crm.communication.service.SmsAlertService;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.notification.entity.Notification;
import com.company.crm.notification.service.NotificationService;
import com.company.crm.user.entity.User;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FR-2.5: reminds a lead's owner (or its creator, when unowned) that a follow-up is due — by email,
 * and by SMS when the tenant enabled the FR-5.3 follow_up_due alert. Each follow-up date is
 * reminded once; the claim makes concurrent runs safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpReminderService {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final LeadRepository leadRepository;
    private final MailService mailService;
    private final SmsAlertService smsAlertService;
    private final NotificationService notificationService;
    private final NotificationSettingsService settings;
    private final NotificationDeliveryLog deliveryLog;

    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return leadRepository.findDueFollowUpIds(LocalDateTime.now(), LeadStage.CONVERTED);
    }

    /** Sends one lead's reminder in its own transaction, so one failure doesn't block the rest. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remind(Long leadId) {
        if (leadRepository.claimFollowUpReminder(leadId, LocalDateTime.now()) == 0) {
            return; // already reminded
        }
        Lead lead = leadRepository.findById(leadId).orElse(null);
        if (lead == null) {
            return;
        }
        User recipient = lead.getOwner() != null ? lead.getOwner() : lead.getCreatedBy();
        if (recipient == null) {
            return;
        }
        Long tenantId = lead.getTenant().getId();
        // NOT-6: each channel as the tenant chose (email and in-app on by default); NOT-8: logged.
        if (settings.isEnabled(tenantId, NotificationEvent.FOLLOW_UP_DUE, NotificationChannel.EMAIL)) {
            try {
                mailService.sendFollowUpReminderEmail(recipient.getEmail(), recipient.getFullName(), lead.getLeadName(),
                        lead.getCompanyName(), DUE.format(lead.getFollowUpDate()), lead.getId());
                deliveryLog.log(tenantId, NotificationEvent.FOLLOW_UP_DUE, "email", Recipient.user(recipient), null, null,
                        NotificationDeliveryLog.SENT, null);
            } catch (MailException | MessagingException e) {
                log.warn("Follow-up reminder email for lead {} failed: {}", leadId, e.getMessage());
                deliveryLog.log(tenantId, NotificationEvent.FOLLOW_UP_DUE, "email", Recipient.user(recipient), null, null,
                        e instanceof MailQueuedException ? NotificationDeliveryLog.QUEUED : NotificationDeliveryLog.FAILED, e.getMessage());
            }
        }
        smsAlertService.onFollowUpDue(lead, recipient);
        // FR-M.4: also in the app's inbox, pushed to the phone.
        if (settings.isEnabled(tenantId, NotificationEvent.FOLLOW_UP_DUE, NotificationChannel.IN_APP)) {
            notificationService.notify(tenantId, recipient.getId(), Notification.FOLLOW_UP,
                    "Follow-up due: " + lead.getLeadName(),
                    (lead.getCompanyName() != null ? lead.getCompanyName() + " · " : "") + "due " + DUE.format(lead.getFollowUpDate()),
                    "lead", lead.getId(), null);
        }
    }
}
