package com.company.crm.support.service;

import com.company.crm.common.enums.RoleType;
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

@Service
@RequiredArgsConstructor
public class ServiceTicketService {

    private final ServiceTicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ServiceTicketMapper ticketMapper;

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
            User currentUser, String status, String priority, Long customerId, String search) {
        List<ServiceTicket> tickets = currentUser.getRole().getName() == RoleType.SERVICE_AGENT
                ? ticketRepository.findByTenantIdAndAssignedTechnicianId(requireTenantId(currentUser), currentUser.getId())
                : ticketRepository.findByTenantId(requireTenantId(currentUser));

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

    @Transactional(readOnly = true)
    public ServiceTicketResDto getTicket(User currentUser, Long ticketId) {
        ServiceTicket ticket = findTicket(currentUser, ticketId);
        assertAccess(currentUser, ticket);
        return ticketMapper.toDto(ticket);
    }

    /** FR-6.4: counts of still-open tickets by SLA status, for the escalation view. */
    @Transactional(readOnly = true)
    public ServiceTicketSummaryDto getSummary(User currentUser) {
        List<ServiceTicket> tickets = currentUser.getRole().getName() == RoleType.SERVICE_AGENT
                ? ticketRepository.findByTenantIdAndAssignedTechnicianId(requireTenantId(currentUser), currentUser.getId())
                : ticketRepository.findByTenantId(requireTenantId(currentUser));

        long onTrack = 0;
        long atRisk = 0;
        long breached = 0;
        for (ServiceTicket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
                continue;
            }
            switch (ticketMapper.slaStatus(ticket)) {
                case "at_risk" -> atRisk++;
                case "breached" -> breached++;
                default -> onTrack++;
            }
        }
        return new ServiceTicketSummaryDto(onTrack, atRisk, breached);
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

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> ApiException.badRequest("Technician not found"));
        if (technician.getTenant() == null || !technician.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Technician must belong to your tenant");
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
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("Customer not found"));
        if (!customer.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Customer must belong to your tenant");
        }
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
        ServiceTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Service ticket not found"));
        if (!ticket.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Service ticket not found");
        }
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

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Service tickets are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
