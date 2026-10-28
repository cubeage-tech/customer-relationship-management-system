package com.company.crm.support.service;

import com.company.crm.common.period.ReportingPeriods;
import com.company.crm.support.repository.ServiceTicketSummaryQuery;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.pagination.PageRequestFactory;
import com.company.crm.common.pagination.PageResponse;
import com.company.crm.support.repository.ServiceTicketSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.company.crm.common.enums.TicketPriority;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.support.dto.request.ServiceTicketFeedbackReqDto;
import com.company.crm.support.dto.request.ServiceTicketReqDto;
import com.company.crm.support.dto.request.ServiceTicketStatusReqDto;
import com.company.crm.support.dto.response.ServiceTicketResDto;
import com.company.crm.support.dto.response.ServiceTicketSummaryDto;
import com.company.crm.support.entity.CustomerAsset;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.mapper.ServiceTicketMapper;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.tenant.service.TenantSettingsService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.company.crm.support.event.TicketStatusChangedEvent;
import com.company.crm.support.event.TicketFeedbackReceivedEvent;
import com.company.crm.support.event.TicketFeedbackRequestedEvent;
import com.company.crm.support.dto.response.PublicFeedbackDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServiceTicketService {

    private final ServiceTicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ServiceTicketMapper ticketMapper;
    private final PageRequestFactory pageRequestFactory;
    private final AuditService auditService;
    private final TenantSettingsService tenantSettingsService;
    private final ServiceTicketSummaryQuery ticketSummaryQuery;
    private final ReportingPeriods reportingPeriods;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomerAssetService assetService;
    private final TicketAssignmentRuleService assignmentRuleService;

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    /** Window for the average-resolution-time figure. */
    private static final int RESOLUTION_AVERAGE_DAYS = 30;

    private static final String AUDIT_ENTITY = "service_ticket";

    /** API sort name → entity property. */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdAt", "createdAt",
            "slaDueAt", "slaDueAt",
            "priority", "priority",
            "status", "status");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));


    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (customer, assignedTechnician, tenant), so the session must stay
    // open through mapping.
    @Transactional(readOnly = true)
    public List<ServiceTicketResDto> listTickets(
            User currentUser, String scope, String status, String priority, Long customerId, String search) {
        if (isAgent(currentUser)) {
            // Agents: their own tickets plus the unassigned queue (Decision 4), filtered in the DB.
            return ticketRepository.findAll(filters(currentUser, scope, status, priority, customerId, search), DEFAULT_SORT)
                    .stream().map(ticketMapper::toDto).toList();
        }

        // Every other role: unchanged.
        List<ServiceTicket> tickets = ticketRepository.findByTenantId(requireTenantId(currentUser));

        return tickets.stream()
                .filter(t -> status == null || status.isBlank() || t.getStatus().getDbValue().equals(status))
                .filter(t -> priority == null || priority.isBlank() || t.getPriority().getDbValue().equals(priority))
                .filter(t -> customerId == null || customerId.equals(t.getCustomer().getId()))
                .filter(t -> search == null || search.isBlank()
                        || t.getSubject().toLowerCase().contains(search.trim().toLowerCase())
                        || t.getCustomer().getCompanyName().toLowerCase().contains(search.trim().toLowerCase()))
                .map(ticketMapper::toDto)
                .toList();
    }

    /** Paged, DB-filtered list (opt-in via page/size). Same visibility rules as {@link #listTickets}. */
    @Transactional(readOnly = true)
    public PageResponse<ServiceTicketResDto> listTicketsPage(
            User currentUser, String scope, String status, String priority, Long customerId, String search,
            Integer page, Integer size, String sort) {
        Pageable pageable = pageRequestFactory.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        return PageResponse.of(
                ticketRepository.findAll(filters(currentUser, scope, status, priority, customerId, search), pageable),
                ticketMapper::toDto);
    }

    @Transactional(readOnly = true)
    public ServiceTicketResDto getTicket(User currentUser, Long ticketId) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertViewAccess(currentUser, ticket);
        return ticketMapper.toDto(ticket);
    }

    /**
     * Decision 4: an agent takes an unassigned ticket. Atomic — one conditional UPDATE, so of two
     * agents claiming at the same moment exactly one wins; the other gets 409. Claiming a ticket
     * you already hold is a no-op that succeeds.
     */
    @Transactional
    public ServiceTicketResDto claimTicket(User agent, Long ticketId) {
        Long tenantId = requireTenantId(agent);
        int updated = ticketRepository.assignIfUnassigned(ticketId, tenantId, agent.getId(), LocalDateTime.now());
        ServiceTicket ticket = findTicket(agent, ticketId); // 404 if not in this tenant

        if (updated == 0) {
            User holder = ticket.getAssignedTechnician();
            if (holder != null && holder.getId().equals(agent.getId())) {
                return ticketMapper.toDto(ticket);
            }
            throw ApiException.conflict("This ticket has already been claimed by " + (holder != null ? holder.getFullName() : "another agent"));
        }

        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.ASSIGNED);
            ticket = ticketRepository.save(ticket);
        }
        auditService.record(agent, AuditAction.TICKET_CLAIMED, AUDIT_ENTITY, ticketId, "claimedBy=" + agent.getId());
        return ticketMapper.toDto(ticket);
    }

    /**
     * FR-6.4 SLA counts plus dashboard figures, aggregated in the database. Service agents get
     * their assigned tickets only (unchanged); other roles the whole tenant.
     */
    @Transactional(readOnly = true)
    public ServiceTicketSummaryDto getSummary(User currentUser) {
        LocalDateTime now = reportingPeriods.now();
        return ticketSummaryQuery.summarize(
                requireTenantId(currentUser),
                isAgent(currentUser) ? currentUser.getId() : null,
                now,
                reportingPeriods.startOfWeekAt(),
                now.minusDays(RESOLUTION_AVERAGE_DAYS));
    }

    @Transactional
    public ServiceTicketResDto createTicket(User currentUser, ServiceTicketReqDto dto) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setTenant(currentUser.getTenant());
        ticket.setCreatedBy(currentUser);
        ticket.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        ticket.setSubject(dto.getSubject());
        ticket.setDescription(dto.getDescription());
        ticket.setPriority(parsePriority(dto.getPriority()));
        ticket.setAsset(dto.getAssetId() != null ? assetService.requireAssetOf(ticket.getCustomer(), dto.getAssetId()) : null);
        return ticketMapper.toDto(openTicket(ticket));
    }

    /**
     * FR-6.1: a ticket raised by the customer through the tenant's public support form. Same SLA and
     * auto-assignment as staff-logged tickets; no creating user.
     */
    @Transactional
    public ServiceTicket createFromSupportForm(Customer customer, String reporterEmail, String subject,
                                               String description, TicketPriority priority, CustomerAsset asset) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setTenant(customer.getTenant());
        ticket.setCustomer(customer);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        ticket.setSource(ServiceTicket.SOURCE_CUSTOMER);
        ticket.setReporterEmail(reporterEmail);
        ticket.setAsset(asset);
        return openTicket(ticket);
    }

    /** Sets the SLA, applies FR-6.3b auto-assignment, and saves a new ticket. */
    private ServiceTicket openTicket(ServiceTicket ticket) {
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setSlaDueAt(LocalDateTime.now().plusHours(slaHours(ticket, ticket.getPriority())));
        assignmentRuleService.apply(ticket).ifPresent(agent -> {
            ticket.setAssignedTechnician(agent);
            ticket.setStatus(TicketStatus.ASSIGNED);
        });
        return ticketRepository.save(ticket);
    }

    @Transactional
    public ServiceTicketResDto updateTicket(User currentUser, Long ticketId, ServiceTicketReqDto dto) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);

        ticket.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        ticket.setSubject(dto.getSubject());
        ticket.setDescription(dto.getDescription());
        ticket.setAsset(dto.getAssetId() != null ? assetService.requireAssetOf(ticket.getCustomer(), dto.getAssetId()) : null);

        TicketPriority newPriority = parsePriority(dto.getPriority());
        if (newPriority != ticket.getPriority()) {
            ticket.setPriority(newPriority);
            ticket.setSlaDueAt(ticket.getCreatedAt().plusHours(slaHours(ticket, newPriority)));
        }

        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    @Transactional
    public ServiceTicketResDto assignTechnician(User currentUser, Long ticketId, Long technicianId) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);

        // Not the blanket assertAccess: a service_agent must be able to claim an
        // *unassigned* ticket (currently owned by no one) or hand off one already
        // theirs — assertAccess would reject both since neither is "already mine".
        // Reassigning someone else's ticket away from them is still blocked.
        if (currentUser.getRole().getName() == RoleType.SERVICE_AGENT
                && ticket.getAssignedTechnician() != null
                && !ticket.getAssignedTechnician().getId().equals(currentUser.getId())) {
            throw ApiException.forbidden("You do not have access to this service ticket");
        }

        User technician = userRepository.findByIdAndTenantId(technicianId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Technician must belong to your tenant"));

        // An agent assigning an unassigned ticket is a claim: do it atomically so two agents
        // can't both take it (the check above alone is a check-then-act race).
        if (isAgent(currentUser) && ticket.getAssignedTechnician() == null) {
            if (ticketRepository.assignIfUnassigned(ticketId, requireTenantId(currentUser), technician.getId(), LocalDateTime.now()) == 0) {
                throw ApiException.conflict("This ticket has just been claimed by someone else");
            }
            ticket = findTicket(currentUser, ticketId);
            if (ticket.getStatus() == TicketStatus.OPEN) {
                ticket.setStatus(TicketStatus.ASSIGNED);
            }
            return ticketMapper.toDto(ticketRepository.save(ticket));
        }

        ticket.setAssignedTechnician(technician);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.ASSIGNED);
        }
        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    /** FR-6.2: move a ticket through Open/Assigned/In Progress/Resolved/Closed. */
    @Transactional
    public ServiceTicketResDto changeStatus(User currentUser, Long ticketId, ServiceTicketStatusReqDto dto) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);

        TicketStatus target = parseStatus(dto.getStatus());
        boolean closingOut = target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED;
        boolean changed = ticket.getStatus() != target;

        ticket.setStatus(target);
        ticket.setResolvedAt(closingOut ? (ticket.getResolvedAt() != null ? ticket.getResolvedAt() : LocalDateTime.now()) : null);
        // FR-6.5: the first resolution sends the customer a feedback link (once per ticket).
        boolean requestFeedback = closingOut && ticket.getFeedbackToken() == null && ticket.getFeedbackScore() == null;
        if (requestFeedback) {
            ticket.setFeedbackToken(newToken());
        }

        ServiceTicketResDto saved = ticketMapper.toDto(ticketRepository.save(ticket));
        if (requestFeedback) {
            eventPublisher.publishEvent(new TicketFeedbackRequestedEvent(ticket.getTenant().getId(), ticket.getId()));
        }
        if (changed) {
            // FR-5.3: SMS alert (if enabled for the tenant) goes out after this transaction commits.
            eventPublisher.publishEvent(new TicketStatusChangedEvent(ticket.getTenant().getId(), ticket.getId(), target));
        }
        return saved;
    }

    /** FR-6.5: recorded by staff on the customer's behalf (customers can also use their emailed link). */
    @Transactional
    public ServiceTicketResDto recordFeedback(User currentUser, Long ticketId, ServiceTicketFeedbackReqDto dto) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);

        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw ApiException.badRequest("Feedback can only be recorded once a ticket is resolved");
        }

        ticket.setFeedbackScore(dto.getScore());
        ticket.setFeedbackComment(dto.getComment());
        ticket.setFeedbackAt(LocalDateTime.now());
        ServiceTicket saved = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketFeedbackReceivedEvent(saved.getTenant().getId(), saved.getId())); // FR-AI-6.1
        return ticketMapper.toDto(saved);
    }

    /** FR-6.5: what the customer's feedback page shows. 404 for an unknown link. */
    @Transactional(readOnly = true)
    public PublicFeedbackDto feedbackPage(String token) {
        ServiceTicket ticket = findByFeedbackToken(token);
        return new PublicFeedbackDto(ticket.getTenant().getCompanyName(), ticket.getSubject(),
                ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getFullName() : null,
                ticket.getResolvedAt(), ticket.getFeedbackScore(), ticket.getFeedbackComment());
    }

    /** FR-6.5: the customer rates the resolved ticket (1-5 + comment) — once. */
    @Transactional
    public PublicFeedbackDto submitCustomerFeedback(String token, ServiceTicketFeedbackReqDto dto) {
        ServiceTicket ticket = findByFeedbackToken(token);
        if (ticket.getFeedbackScore() != null) {
            throw ApiException.conflict("Thanks — feedback for this request has already been received");
        }
        ticket.setFeedbackScore(dto.getScore());
        ticket.setFeedbackComment(dto.getComment() != null && !dto.getComment().isBlank() ? dto.getComment().trim() : null);
        ticket.setFeedbackAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketFeedbackReceivedEvent(ticket.getTenant().getId(), ticket.getId())); // FR-AI-6.1
        return feedbackPage(token);
    }

    private ServiceTicket findByFeedbackToken(String token) {
        return (token == null || token.isBlank() ? Optional.<ServiceTicket>empty() : ticketRepository.findByFeedbackToken(token))
                .orElseThrow(() -> ApiException.notFound("This feedback link is not valid"));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ==================== Helpers ====================

    /** NFR-7.6b: the tenant's SLA window for this priority (or the deployment default). */
    private long slaHours(ServiceTicket ticket, TicketPriority priority) {
        return tenantSettingsService.slaHours(ticket.getTenant().getId(), priority);
    }

    private Customer resolveCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Customer must belong to your tenant"));
        return customer;
    }

    private TicketPriority parsePriority(String rawPriority) {
        try {
            return TicketPriority.fromDbValue(rawPriority);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown ticket priority: " + rawPriority);
        }
    }

    private TicketStatus parseStatus(String rawStatus) {
        try {
            return TicketStatus.fromDbValue(rawStatus);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown ticket status: " + rawStatus);
        }
    }

    /** DB-E16: the ticket if the caller may view it (agents also see the unassigned queue). */
    @Transactional(readOnly = true)
    public ServiceTicket requireViewable(User currentUser, Long ticketId) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertViewAccess(currentUser, ticket);
        return ticket;
    }

    /** DB-E16: the ticket if the caller may change it (agents: only their own). */
    @Transactional(readOnly = true)
    public ServiceTicket requireEditable(User currentUser, Long ticketId) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);
        return ticket;
    }

    private ServiceTicket findTicket(User currentUser, Long ticketId) {
        ServiceTicket ticket = ticketRepository.findByIdAndTenantId(ticketId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Service ticket not found"));
        return ticket;
    }

    /** service_agent may only access tickets assigned to them ("assigned" data scope). */
    private void assertAccess(User currentUser, ServiceTicket ticket) {
        if (currentUser.getRole().getName() == RoleType.SERVICE_AGENT
                && (ticket.getAssignedTechnician() == null
                        || !ticket.getAssignedTechnician().getId().equals(currentUser.getId()))) {
            throw ApiException.forbidden("You do not have access to this service ticket");
        }
    }

    /** Reading is wider than editing for agents: they may open unassigned queue tickets before claiming. */
    private void assertViewAccess(User currentUser, ServiceTicket ticket) {
        if (isAgent(currentUser) && ticket.getAssignedTechnician() == null) {
            return;
        }
        assertAccess(currentUser, ticket);
    }

    private boolean isAgent(User user) {
        return user.getRole().getName() == RoleType.SERVICE_AGENT;
    }

    /** Tenant + role visibility + optional filters, all evaluated in the database. */
    private Specification<ServiceTicket> filters(User currentUser, String scope, String status, String priority,
                                                 Long customerId, String search) {
        Specification<ServiceTicket> spec = ServiceTicketSpecifications.inTenant(requireTenantId(currentUser));
        if (isAgent(currentUser)) {
            spec = spec.and(ServiceTicketSpecifications.forAgent(currentUser.getId(), TicketScope.parse(scope)));
        }
        return spec
                .and(ServiceTicketSpecifications.hasStatus(status == null || status.isBlank() ? null : parseStatus(status)))
                .and(ServiceTicketSpecifications.hasPriority(priority == null || priority.isBlank() ? null : parsePriority(priority)))
                .and(ServiceTicketSpecifications.forCustomer(customerId))
                .and(ServiceTicketSpecifications.matches(search));
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Service tickets are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
