package com.company.crm.support.mapper;

import com.company.crm.common.enums.TicketStatus;
import com.company.crm.support.dto.response.ServiceTicketResDto;
import com.company.crm.support.entity.ServiceTicket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class ServiceTicketMapper {

    public ServiceTicketResDto toDto(ServiceTicket ticket) {
        return new ServiceTicketResDto(
                ticket.getId(),
                ticket.getCustomer().getId(),
                ticket.getCustomer().getCompanyName(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getPriority().getDbValue(),
                ticket.getStatus().getDbValue(),
                ticket.getSlaDueAt(),
                slaStatus(ticket),
                ticket.getResolvedAt(),
                ticket.getFeedbackScore(),
                ticket.getFeedbackComment(),
                ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getId() : null,
                ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getFullName() : null,
                ticket.getCreatedAt()
        );
    }

    /** FR-6.4: on_track / at_risk (within 25% of the SLA window remaining) / breached / met. */
    public String slaStatus(ServiceTicket ticket) {
        boolean closedOut = ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED;

        if (closedOut) {
            LocalDateTime resolvedAt = ticket.getResolvedAt();
            boolean metDeadline = resolvedAt != null && !resolvedAt.isAfter(ticket.getSlaDueAt());
            return metDeadline ? "met" : "breached";
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getSlaDueAt())) {
            return "breached";
        }

        Duration totalWindow = Duration.between(ticket.getCreatedAt(), ticket.getSlaDueAt());
        Duration remaining = Duration.between(now, ticket.getSlaDueAt());
        boolean atRisk = totalWindow.isZero() || remaining.toMillis() <= totalWindow.toMillis() / 4;
        return atRisk ? "at_risk" : "on_track";
    }
}
