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
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.mapper.ServiceTicketMapper;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServiceTicketService {

    private final ServiceTicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ServiceTicketMapper ticketMapper;
    private final PageRequestFactory pageRequestFactory;
    private final AuditService auditService;
    private final ServiceTicketSummaryQuery ticketSummaryQuery;
    private final ReportingPeriods reportingPeriods;

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

    @Value("${app.ticket.sla-hours.critical}")
    private long slaHoursCritical;

    @Value("${app.ticket.sla-hours.high}")
    private long slaHoursHigh;

    @Value("${app.ticket.sla-hours.medium}")
    private long slaHoursMedium;

    @Value("${app.ticket.sla-hours.low}")
    private long slaHoursLow;

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
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setSlaDueAt(LocalDateTime.now().plusHours(slaHours(ticket.getPriority())));
        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    @Transactional
    public ServiceTicketResDto updateTicket(User currentUser, Long ticketId, ServiceTicketReqDto dto) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);

        ticket.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        ticket.setSubject(dto.getSubject());
        ticket.setDescription(dto.getDescription());

        TicketPriority newPriority = parsePriority(dto.getPriority());
        if (newPriority != ticket.getPriority()) {
            ticket.setPriority(newPriority);
            ticket.setSlaDueAt(ticket.getCreatedAt().plusHours(slaHours(newPriority)));
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

        ticket.setStatus(target);
        ticket.setResolvedAt(closingOut ? (ticket.getResolvedAt() != null ? ticket.getResolvedAt() : LocalDateTime.now()) : null);

        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    /** FR-6.5: recorded on the customer's behalf by internal staff — no self-service portal yet. */
    @Transactional
    public ServiceTicketResDto recordFeedback(User currentUser, Long ticketId, ServiceTicketFeedbackReqDto dto) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);

        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw ApiException.badRequest("Feedback can only be recorded once a ticket is resolved");
        }

        ticket.setFeedbackScore(dto.getScore());
        ticket.setFeedbackComment(dto.getComment());
        return ticketMapper.toDto(ticketRepository.save(ticket));
    }

    // ==================== Helpers ====================

    private long slaHours(TicketPriority priority) {
        return switch (priority) {
            case CRITICAL -> slaHoursCritical;
            case HIGH -> slaHoursHigh;
            case MEDIUM -> slaHoursMedium;
            case LOW -> slaHoursLow;
        };
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
