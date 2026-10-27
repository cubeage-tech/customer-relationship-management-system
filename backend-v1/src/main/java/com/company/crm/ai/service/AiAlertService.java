package com.company.crm.ai.service;

import com.company.crm.ai.privacy.AiRedactor;
import com.company.crm.ai.privacy.AiPrivacyService;
import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.RoleType;
import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.event.InboundMessageRecordedEvent;
import com.company.crm.communication.repository.CommunicationRepo;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationChannel;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.settings.NotificationSettingsService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NOT-5 AI alerts: when a customer's (or lead's) email, WhatsApp or SMS reads as unhappy — high
 * churn risk — its owner is told (the tenant's admins if it has none). Off until a tenant admin
 * switches it on (it sends message text to the AI provider); email addresses and phone numbers are
 * removed from the text first. Runs in the background so the provider's webhook isn't held up.
 */
@Service
@Slf4j
public class AiAlertService {

    private static final Set<CommunicationChannel> CHANNELS = Set.of(CommunicationChannel.EMAIL, CommunicationChannel.WHATSAPP, CommunicationChannel.SMS);
    private static final int MIN_LENGTH = 15;
    private static final int MAX_LENGTH = 4000;

    private final LlmGateway llm;
    private final CommunicationRepo communicationRepo;
    private final NotificationSettingsService settings;
    private final StaffNotifier staffNotifier;
    private final UserRepository userRepository;
    private final AiPrivacyService privacy;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-alerts");
        t.setDaemon(true);
        return t;
    });

    public AiAlertService(LlmGateway llm, CommunicationRepo communicationRepo, NotificationSettingsService settings,
                          StaffNotifier staffNotifier, UserRepository userRepository, AiPrivacyService privacy,
                          PlatformTransactionManager transactionManager) {
        this.privacy = privacy;
        this.llm = llm;
        this.communicationRepo = communicationRepo;
        this.settings = settings;
        this.staffNotifier = staffNotifier;
        this.userRepository = userRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onInbound(InboundMessageRecordedEvent event) {
        // SEC-5: message text goes to the AI provider only if the tenant allows it.
        if (!llm.isConfigured() || !privacy.messageContentAllowed(event.tenantId())
                || (!settings.isEnabled(event.tenantId(), NotificationEvent.AI_CHURN_RISK, NotificationChannel.IN_APP)
                && !settings.isEnabled(event.tenantId(), NotificationEvent.AI_CHURN_RISK, NotificationChannel.EMAIL))) {
            return;
        }
        worker.execute(() -> {
            try {
                check(event.communicationId());
            } catch (RuntimeException e) {
                log.warn("AI alert check for message {} failed: {}", event.communicationId(), e.getMessage());
            }
        });
    }

    /** Analyses one inbound message and alerts its owner when the customer sounds likely to leave. */
    void check(Long communicationId) {
        String text = tx.execute(s -> communicationRepo.findById(communicationId).filter(c -> CHANNELS.contains(c.getChannel()))
                // SEC-6: never for a customer or lead excluded from AI
                .filter(c -> (c.getCustomer() == null || !c.getCustomer().isAiExcluded()) && (c.getLead() == null || !c.getLead().isAiExcluded()))
                .map(c -> redact(((c.getSubject() != null ? c.getSubject() + "\n" : "") + (c.getBody() != null ? c.getBody() : "")).trim()))
                .orElse(""));
        if (text == null || text.length() < MIN_LENGTH) {
            return;
        }
        Optional<SentimentJudgement> judgement = llm.structured(AiWritingService.SENTIMENT_SYSTEM,
                "Message:\n" + (text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text), SentimentJudgement.class);
        if (judgement.isEmpty() || !"high".equals(judgement.get().churnRisk())) {
            return;
        }
        String reason = judgement.get().reason();
        tx.executeWithoutResult(s -> communicationRepo.findById(communicationId).ifPresent(c -> alert(c, reason)));
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

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }
}
