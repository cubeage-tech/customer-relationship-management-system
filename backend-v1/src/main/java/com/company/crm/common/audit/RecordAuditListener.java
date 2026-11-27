package com.company.crm.common.audit;

import com.company.crm.analytics.entity.ReportSchedule;
import com.company.crm.attachment.entity.Attachment;
import com.company.crm.campaign.entity.Campaign;
import com.company.crm.campaign.entity.CustomerSegment;
import com.company.crm.campaign.entity.Promotion;
import com.company.crm.customer.entity.CustomerDocument;
import com.company.crm.industry.entity.Industry;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.entity.LeadAssignmentRule;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.quotation.entity.Product;
import com.company.crm.sales.entity.PipelineStage;
import com.company.crm.salesforce.entity.SalesTarget;
import com.company.crm.support.entity.CustomerAsset;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.entity.TicketAssignmentRule;
import com.company.crm.user.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * SEC-7: every create, update and delete a signed-in user makes to the records below is in the audit
 * trail, without each service having to remember it. Records with their own, more specific audit
 * entries (customers, opportunities, quotations, users, payments...) are not listed. Only the names
 * of changed fields are kept, never their values; scheduled jobs and webhooks (no user) aren't
 * recorded here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecordAuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private record Audited(String entityType, Function<Object, String> label) {}

    private static <T> Map.Entry<Class<?>, Audited> audited(Class<T> type, String entityType, Function<T, String> label) {
        return Map.entry(type, new Audited(entityType, entity -> label.apply(type.cast(entity))));
    }

    private static final Map<Class<?>, Audited> AUDITED = Map.ofEntries(
            audited(Lead.class, "lead", Lead::getLeadName),
            audited(ServiceTicket.class, "service_ticket", ServiceTicket::getSubject),
            audited(Campaign.class, "campaign", Campaign::getName),
            audited(Product.class, "product", Product::getName),
            audited(Meeting.class, "meeting", Meeting::getTitle),
            audited(CustomerDocument.class, "customer_document", CustomerDocument::getFileName),
            audited(Attachment.class, "attachment", Attachment::getFileName),
            audited(PipelineStage.class, "pipeline_stage", PipelineStage::getLabel),
            audited(CustomerSegment.class, "segment", CustomerSegment::getName),
            audited(Promotion.class, "promotion", Promotion::getName),
            audited(SalesTarget.class, "sales_target", target -> null),
            audited(LeadAssignmentRule.class, "lead_assignment_rule", LeadAssignmentRule::getName),
            audited(TicketAssignmentRule.class, "ticket_assignment_rule", TicketAssignmentRule::getName),
            audited(CustomerAsset.class, "customer_asset", CustomerAsset::getName),
            audited(Industry.class, "industry", Industry::getLabel),
            audited(ReportSchedule.class, "report_schedule", ReportSchedule::getName));

    /** Bookkeeping the application updates itself — a change to only these isn't worth an entry. */
    private static final Set<String> IGNORED_FIELDS = Set.of("createdAt", "updatedAt", "emailHash", "phoneHash",
            "lastRunAt", "nextRunAt", "lastStatus", "lastError", "aiScore", "aiScoreLabel", "aiScoredAt");

    private static final int MAX_DETAILS = 1000;

    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        record(event.getEntity(), event.getId(), AuditAction.RECORD_CREATED, null);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        int[] dirty = event.getDirtyProperties();
        String change = null;
        if (dirty != null) {
            String[] names = event.getPersister().getPropertyNames();
            List<String> changed = Arrays.stream(dirty).mapToObj(i -> names[i]).filter(n -> !IGNORED_FIELDS.contains(n)).toList();
            if (changed.isEmpty()) {
                return;
            }
            change = "changed: " + String.join(", ", changed);
        }
        record(event.getEntity(), event.getId(), AuditAction.RECORD_UPDATED, change);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        record(event.getEntity(), event.getId(), AuditAction.RECORD_DELETED, null);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    /** Written on the request's own connection, so it commits — or rolls back — with the change. */
    private void record(Object entity, Object id, AuditAction action, String change) {
        Audited audited = AUDITED.get(entity.getClass());
        User actor = currentUser();
        if (audited == null || actor == null) {
            return;
        }
        String label = audited.label().apply(entity);
        String details = label == null ? change : change == null ? label : label + " — " + change;
        if (details != null && details.length() > MAX_DETAILS) {
            details = details.substring(0, MAX_DETAILS);
        }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_user_id, action, entity_type, entity_id, details) VALUES (?, ?, ?, ?, ?, ?)",
                actor.getTenant() != null ? actor.getTenant().getId() : null, actor.getId(), action.name(), audited.entityType(),
                id instanceof Number n ? n.longValue() : null, details);
        log.info("audit action={} entity={}#{}", action, audited.entityType(), id);
    }

    private static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user ? user : null;
    }
}
