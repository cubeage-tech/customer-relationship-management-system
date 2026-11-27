package com.company.crm.support.service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.enums.TicketPriority;
import com.company.crm.common.exception.ApiException;
import com.company.crm.support.dto.request.TicketAssignmentRuleReqDto;
import com.company.crm.support.dto.response.TicketAssignmentRuleResDto;
import com.company.crm.support.entity.ServiceTicket;
import com.company.crm.support.entity.TicketAssignmentRule;
import com.company.crm.support.repository.TicketAssignmentRuleRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * FR-6.3b: configurable auto-assignment of new service tickets. The first active rule matching the
 * ticket's priority and the customer's industry assigns a named service agent, or rotates
 * round-robin over the tenant's active service agents. With no match (or no available agent) the
 * ticket stays unassigned in the agents' claim queue, as before.
 */
@Service
@RequiredArgsConstructor
public class TicketAssignmentRuleService {

    private final TicketAssignmentRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<TicketAssignmentRuleResDto> listRules(User currentUser) {
        return ruleRepository.findByTenantIdOrderByPriorityAscIdAsc(requireTenantId(currentUser)).stream().map(this::toDto).toList();
    }

    @Transactional
    public TicketAssignmentRuleResDto createRule(User currentUser, TicketAssignmentRuleReqDto dto) {
        TicketAssignmentRule rule = new TicketAssignmentRule();
        rule.setTenant(currentUser.getTenant());
        applyFields(currentUser, rule, dto);
        return toDto(ruleRepository.save(rule));
    }

    @Transactional
    public TicketAssignmentRuleResDto updateRule(User currentUser, Long ruleId, TicketAssignmentRuleReqDto dto) {
        TicketAssignmentRule rule = findRule(currentUser, ruleId);
        applyFields(currentUser, rule, dto);
        return toDto(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(User currentUser, Long ruleId) {
        ruleRepository.delete(findRule(currentUser, ruleId));
    }

    /** Assigns the new ticket per the first matching rule; returns the agent, or empty when it stays queued. */
    @Transactional
    public Optional<User> apply(ServiceTicket ticket) {
        Long tenantId = ticket.getTenant().getId();
        for (TicketAssignmentRule rule : ruleRepository.findByTenantIdOrderByPriorityAscIdAsc(tenantId)) {
            if (!rule.matches(ticket)) {
                continue;
            }
            if (rule.getAssignUser() != null) {
                if (isAvailableAgent(rule.getAssignUser())) {
                    return Optional.of(rule.getAssignUser());
                }
                continue;
            }
            // Round-robin: re-read the cursor under a row lock so concurrent tickets go to different agents.
            entityManager.refresh(rule, LockModeType.PESSIMISTIC_WRITE);
            Optional<User> next = nextAgent(tenantId, rule.getLastAssignedUserId());
            if (next.isPresent()) {
                rule.setLastAssignedUserId(next.get().getId());
                return next;
            }
        }
        return Optional.empty();
    }

    // ==================== Helpers ====================

    private Optional<User> nextAgent(Long tenantId, Long last) {
        List<User> agents = userRepository.findByTenantId(tenantId).stream()
                .filter(TicketAssignmentRuleService::isAvailableAgent)
                .sorted(Comparator.comparing(User::getId))
                .toList();
        return agents.stream().filter(u -> last == null || u.getId() > last).findFirst()
                .or(() -> agents.stream().findFirst());
    }

    private static boolean isAvailableAgent(User user) {
        return user.getRole().getName() == RoleType.SERVICE_AGENT && user.getStatus() == AccountStatus.ACTIVE;
    }

    private void applyFields(User currentUser, TicketAssignmentRule rule, TicketAssignmentRuleReqDto dto) {
        rule.setName(dto.getName().trim());
        rule.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
        rule.setActive(dto.getActive() == null || dto.getActive());
        rule.setMatchPriority(isBlank(dto.getMatchPriority()) ? null : parsePriority(dto.getMatchPriority()));
        rule.setMatchIndustry(isBlank(dto.getMatchIndustry()) ? null : dto.getMatchIndustry().trim());
        if (dto.getAssignUserId() != null) {
            User agent = userRepository.findByIdAndTenantId(dto.getAssignUserId(), requireTenantId(currentUser))
                    .orElseThrow(() -> ApiException.badRequest("User must belong to your tenant"));
            if (agent.getRole().getName() != RoleType.SERVICE_AGENT) {
                throw ApiException.badRequest("Tickets can only be auto-assigned to a service agent");
            }
            rule.setAssignUser(agent);
        } else {
            rule.setAssignUser(null);
        }
    }

    private TicketAssignmentRule findRule(User currentUser, Long ruleId) {
        return ruleRepository.findByIdAndTenantId(ruleId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Assignment rule not found"));
    }

    private TicketAssignmentRuleResDto toDto(TicketAssignmentRule r) {
        return new TicketAssignmentRuleResDto(r.getId(), r.getName(), r.getPriority(), r.isActive(),
                r.getMatchPriority() != null ? r.getMatchPriority().getDbValue() : null, r.getMatchIndustry(),
                r.getAssignUser() != null ? r.getAssignUser().getId() : null,
                r.getAssignUser() != null ? r.getAssignUser().getFullName() : null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static TicketPriority parsePriority(String raw) {
        try {
            return TicketPriority.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown ticket priority: " + raw);
        }
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Assignment rules are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
