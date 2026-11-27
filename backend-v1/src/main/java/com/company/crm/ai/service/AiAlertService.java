package com.company.crm.ai.service;

import com.company.crm.ai.privacy.AiRedactor;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.repository.CommunicationRepo;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationChannel;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.settings.NotificationSettingsService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * NOT-5 AI alerts: when a customer's (or lead's) email, WhatsApp or SMS reads as unhappy — high
 * churn risk — its owner is told (the tenant's admins if it has none). Off until a tenant admin
 * switches it on. The message is classified by {@link SentimentService} (FR-AI-6.1), which runs
 * whenever these alerts are on and hands each reading here.
 */
@Service
public class AiAlertService {

    private final CommunicationRepo communicationRepo;
    private final NotificationSettingsService settings;
    private final StaffNotifier staffNotifier;
    private final UserRepository userRepository;
    private final TransactionTemplate tx;

    public AiAlertService(CommunicationRepo communicationRepo, NotificationSettingsService settings, StaffNotifier staffNotifier,
                          UserRepository userRepository, PlatformTransactionManager transactionManager) {
        this.communicationRepo = communicationRepo;
        this.settings = settings;
        this.staffNotifier = staffNotifier;
        this.userRepository = userRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Whether the tenant wants churn-risk alerts (in the app or by email). */
    public boolean enabled(Long tenantId) {
        return settings.isEnabled(tenantId, NotificationEvent.AI_CHURN_RISK, NotificationChannel.IN_APP)
                || settings.isEnabled(tenantId, NotificationEvent.AI_CHURN_RISK, NotificationChannel.EMAIL);
    }

    /** After an inbound message was classified: alerts its owner when the customer sounds likely to leave. */
    public void onReading(Long communicationId, SentimentService.Reading reading) {
        if (!"high".equals(reading.churnRisk())) {
            return;
        }
        tx.executeWithoutResult(s -> communicationRepo.findById(communicationId)
                .filter(c -> enabled(c.getTenant().getId()))
                .ifPresent(c -> alert(c, reading.reason())));
    }

    private void alert(Communication c, String reason) {
        Long tenantId = c.getTenant().getId();
        boolean customer = c.getCustomer() != null;
        User owner = customer ? c.getCustomer().getOwner() : c.getLead() != null ? c.getLead().getOwner() : null;
        List<User> recipients = owner != null && owner.getStatus() == AccountStatus.ACTIVE ? List.of(owner)
                : userRepository.findByTenantId(tenantId).stream()
                .filter(u -> u.getRole().getName() == RoleType.ADMIN && u.getStatus() == AccountStatus.ACTIVE).toList();
        String name = customer ? c.getCustomer().getCompanyName() : c.getLead() != null ? c.getLead().getLeadName() : "a contact";
        Long recordId = customer ? c.getCustomer().getId() : c.getLead() != null ? c.getLead().getId() : null;
        String entity = customer ? "customer" : "lead";
        staffNotifier.notify(tenantId, NotificationEvent.AI_CHURN_RISK, recipients,
                "Unhappy message from " + name,
                "AI: high churn risk in their " + c.getChannel().getDbValue() + " message. " + (reason != null ? reason : ""),
                recordId != null ? entity : null, recordId, null, recordId != null ? "/" + entity + "s/" + recordId : null);
    }

    /** Removes email addresses, phone and ID numbers before the text leaves the CRM (SEC-5). */
    static String redact(String text) {
        return AiRedactor.redact(text);
    }
}
