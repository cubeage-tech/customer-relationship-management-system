package com.company.crm.ai.service;

import com.company.crm.ai.dto.AiDtos.SentimentJudgement;
import com.company.crm.ai.llm.LlmGateway;
import com.company.crm.ai.privacy.AiPrivacyService;
import com.company.crm.ai.privacy.AiRedactor;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.communication.entity.Communication;
import com.company.crm.communication.event.InboundMessageRecordedEvent;
import com.company.crm.communication.repository.CommunicationRepo;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.event.TicketFeedbackReceivedEvent;
import com.company.crm.support.repository.ServiceTicketRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FR-AI-6.1: classifies the sentiment of what customers write — emails, WhatsApp and SMS (chat)
 * messages, and the comment on their ticket feedback (their review) — as positive, neutral or
 * negative, with a score, the churn risk and the reason, and stores it on the message or ticket.
 * Messages are classified as they arrive when the tenant switched that on (or NOT-5 alerts are on),
 * in the background so the provider's webhook isn't held up; any one can be classified on request.
 * Only when message text may go to the AI (SEC-5), never for records excluded from AI (SEC-6), and
 * email addresses and phone numbers are removed first.
 */
@Service
@Slf4j
public class SentimentService {

    private static final Set<String> SENTIMENTS = Set.of("positive", "neutral", "negative");
    private static final Set<String> RISKS = Set.of("low", "medium", "high");
    private static final Set<CommunicationChannel> CHANNELS = Set.of(CommunicationChannel.EMAIL, CommunicationChannel.WHATSAPP, CommunicationChannel.SMS);
    private static final int MIN_LENGTH = 15;
    private static final int MAX_LENGTH = 4000;
    private static final int MAX_REASON = 300;

    /** A normalised judgement: score within -1..1 (two decimals), known labels only. */
    public record Reading(String sentiment, double score, String churnRisk, String reason) {}

    private final LlmGateway llm;
    private final AiPrivacyService privacy;
    private final AiAlertService alerts;
    private final ChurnRiskService churnRisk;
    private final CommunicationRepo communicationRepo;
    private final ServiceTicketRepository ticketRepository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-sentiment");
        t.setDaemon(true);
        return t;
    });

    public SentimentService(LlmGateway llm, AiPrivacyService privacy, AiAlertService alerts, ChurnRiskService churnRisk,
                            CommunicationRepo communicationRepo, ServiceTicketRepository ticketRepository, JdbcTemplate jdbc,
                            PlatformTransactionManager transactionManager) {
        this.llm = llm;
        this.privacy = privacy;
        this.alerts = alerts;
        this.churnRisk = churnRisk;
        this.communicationRepo = communicationRepo;
        this.ticketRepository = ticketRepository;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Judges a text; empty when the model declines. */
    public Optional<Reading> judge(String text) {
        String clean = AiRedactor.redact(text.trim());
        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH);
        }
        return llm.structured(AiWritingService.SENTIMENT_SYSTEM, "Message:\n" + clean, SentimentJudgement.class).map(SentimentService::normalise);
    }

    static Reading normalise(SentimentJudgement j) {
        String reason = j.reason() == null ? null : j.reason().length() > MAX_REASON ? j.reason().substring(0, MAX_REASON) : j.reason();
        double score = BigDecimal.valueOf(Math.max(-1, Math.min(1, j.score()))).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new Reading(SENTIMENTS.contains(j.sentiment()) ? j.sentiment() : "neutral", score,
                RISKS.contains(j.churnRisk()) ? j.churnRisk() : "medium", reason);
    }

    public void storeForMessage(Long communicationId, Reading r) {
        jdbc.update("UPDATE communication_logs SET sentiment = ?, sentiment_score = ?, churn_risk = ?, sentiment_reason = ?, sentiment_at = now() "
                + "WHERE id = ?", r.sentiment(), r.score(), r.churnRisk(), r.reason(), communicationId);
        churnRisk.refreshForMessage(communicationId); // FR-AI-6.2
    }

    public void storeForFeedback(Long ticketId, Reading r) {
        jdbc.update("UPDATE service_tickets SET feedback_sentiment = ?, feedback_sentiment_score = ?, feedback_churn_risk = ?, "
                + "feedback_sentiment_reason = ?, feedback_sentiment_at = now() WHERE id = ?", r.sentiment(), r.score(), r.churnRisk(), r.reason(), ticketId);
        churnRisk.refreshForTicket(ticketId); // FR-AI-6.2
    }

    /** What is judged for a message: its subject and text. */
    public static String messageText(Communication c) {
        return ((c.getSubject() != null ? c.getSubject() + "\n" : "") + (c.getBody() != null ? c.getBody() : "")).trim();
    }

    /** What is judged for feedback: the rating and the comment. */
    public static String feedbackText(Integer score, String comment) {
        return ((score != null ? "Customer rating: " + score + " of 5\n" : "") + "Review: " + (comment == null ? "" : comment.trim())).trim();
    }

    // ==================== as messages and feedback arrive ====================

    /** Whether new messages and feedback of the tenant are classified as they arrive. */
    boolean automatic(Long tenantId) {
        return llm.isConfigured() && privacy.messageContentAllowed(tenantId) && (privacy.autoSentiment(tenantId) || alerts.enabled(tenantId));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onInbound(InboundMessageRecordedEvent event) {
        if (automatic(event.tenantId())) {
            background("message " + event.communicationId(), () -> classifyMessage(event.communicationId()));
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onFeedback(TicketFeedbackReceivedEvent event) {
        if (automatic(event.tenantId())) {
            background("feedback on ticket " + event.ticketId(), () -> classifyFeedback(event.ticketId()));
        }
    }

    void classifyMessage(Long communicationId) {
        String text = tx.execute(s -> communicationRepo.findById(communicationId)
                .filter(c -> CHANNELS.contains(c.getChannel()) && c.getDirection() == CommunicationDirection.INBOUND)
                .filter(c -> (c.getCustomer() == null || !c.getCustomer().isAiExcluded()) && (c.getLead() == null || !c.getLead().isAiExcluded()))
                .map(SentimentService::messageText).orElse(""));
        if (text == null || text.length() < MIN_LENGTH) {
            return;
        }
        judge(text).ifPresent(r -> {
            storeForMessage(communicationId, r);
            alerts.onReading(communicationId, r); // NOT-5
        });
    }

    void classifyFeedback(Long ticketId) {
        String text = tx.execute(s -> ticketRepository.findById(ticketId)
                .filter(t -> t.getFeedbackComment() != null && !t.getFeedbackComment().isBlank() && !t.getCustomer().isAiExcluded())
                .map((ServiceTicket t) -> feedbackText(t.getFeedbackScore(), t.getFeedbackComment())).orElse(""));
        if (text == null || text.isEmpty()) {
            return;
        }
        judge(text).ifPresent(r -> storeForFeedback(ticketId, r));
    }

    private void background(String what, Runnable task) {
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Sentiment of {} could not be classified: {}", what, e.getMessage());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }
}
