package com.company.crm.lead.service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.IndustryType;
import com.company.crm.common.enums.LeadSource;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.lead.dto.request.LeadAssignmentRuleReqDto;
import com.company.crm.lead.dto.response.LeadAssignmentRuleResDto;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.entity.LeadAssignmentRule;
import com.company.crm.lead.repository.LeadAssignmentRuleRepository;
import com.company.crm.sales_team.repository.SalesTeamRepository;
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
import java.util.Set;

/**
 * FR-2.3: configurable auto-assignment of new leads. The tenant admin keeps an ordered rule list;
 * the first active rule matching the lead's source/industry assigns a named user, or a team whose
 * sales executives take turns (round-robin). Rules whose target can't take leads are skipped.
 */
@Service
@RequiredArgsConstructor
public class LeadAssignmentRuleService {

    /** Roles that can be a rule's named owner. */
    private static final Set<RoleType> ASSIGNABLE_ROLES = Set.of(RoleType.SALES_EXECUTIVE, RoleType.SALES_MANAGER);

    private final LeadAssignmentRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final SalesTeamRepository salesTeamRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<LeadAssignmentRuleResDto> listRules(User currentUser) {
        return ruleRepository.findByTenantIdOrderByPriorityAscIdAsc(requireTenantId(currentUser)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public LeadAssignmentRuleResDto createRule(User currentUser, LeadAssignmentRuleReqDto dto) {
        LeadAssignmentRule rule = new LeadAssignmentRule();
        rule.setTenant(currentUser.getTenant());
        applyFields(currentUser, rule, dto);
        return toDto(ruleRepository.save(rule));
    }

    @Transactional
    public LeadAssignmentRuleResDto updateRule(User currentUser, Long ruleId, LeadAssignmentRuleReqDto dto) {
        LeadAssignmentRule rule = findRule(currentUser, ruleId);
        Long previousTeamId = rule.getAssignTeam() != null ? rule.getAssignTeam().getId() : null;
        applyFields(currentUser, rule, dto);
        if (rule.getAssignTeam() == null || !rule.getAssignTeam().getId().equals(previousTeamId)) {
            rule.setLastAssignedUserId(null);
        }
        return toDto(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(User currentUser, Long ruleId) {
        ruleRepository.delete(findRule(currentUser, ruleId));
    }

    /**
     * Applies the first matching rule to a new lead (owner and/or team). Returns false when no rule
     * applied, leaving the lead untouched. Called inside lead creation's transaction.
     */
    @Transactional
    public boolean apply(Lead lead) {
        Long tenantId = lead.getTenant().getId();
        for (LeadAssignmentRule rule : ruleRepository.findByTenantIdOrderByPriorityAscIdAsc(tenantId)) {
            if (!rule.matches(lead)) {
                continue;
            }
            if (rule.getAssignUser() != null) {
                if (canOwnLeads(rule.getAssignUser())) {
                    lead.setOwner(rule.getAssignUser());
                    return true;
                }
                continue;
            }
            // Re-read the round-robin cursor under a row lock so concurrent leads don't go to the same member.
            entityManager.refresh(rule, LockModeType.PESSIMISTIC_WRITE);
            lead.setTeam(rule.getAssignTeam());
            nextMember(tenantId, rule).ifPresent(member -> {
                lead.setOwner(member);
                rule.setLastAssignedUserId(member.getId());
            });
            return true;
        }
        return false;
    }

    // ==================== Helpers ====================

    /** Round-robin: the active sales executive after the previous one (by id), wrapping around. */
    private Optional<User> nextMember(Long tenantId, LeadAssignmentRule rule) {
        List<User> members = userRepository.findByTenantIdAndTeamId(tenantId, rule.getAssignTeam().getId()).stream()
                .filter(u -> u.getRole().getName() == RoleType.SALES_EXECUTIVE && u.getStatus() == AccountStatus.ACTIVE)
                .sorted(Comparator.comparing(User::getId))
                .toList();
        Long last = rule.getLastAssignedUserId();
        return members.stream()
                .filter(u -> last == null || u.getId() > last)
                .findFirst()
                .or(() -> members.stream().findFirst());
    }

    private static boolean canOwnLeads(User user) {
        return ASSIGNABLE_ROLES.contains(user.getRole().getName()) && user.getStatus() == AccountStatus.ACTIVE;
    }

    private void applyFields(User currentUser, LeadAssignmentRule rule, LeadAssignmentRuleReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        if ((dto.getAssignUserId() == null) == (dto.getAssignTeamId() == null)) {
            throw ApiException.badRequest("Choose either a user or a team to assign leads to");
        }
        rule.setName(dto.getName().trim());
        rule.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
        rule.setActive(dto.getActive() == null || dto.getActive());
        rule.setMatchSource(isBlank(dto.getMatchSource()) ? null : parseSource(dto.getMatchSource()));
        rule.setMatchIndustry(isBlank(dto.getMatchIndustry()) ? null : parseIndustry(dto.getMatchIndustry()));
        if (dto.getAssignUserId() != null) {
            User user = userRepository.findByIdAndTenantId(dto.getAssignUserId(), tenantId)
                    .orElseThrow(() -> ApiException.badRequest("User must belong to your tenant"));
            if (!ASSIGNABLE_ROLES.contains(user.getRole().getName())) {
                throw ApiException.badRequest("Leads can only be auto-assigned to a sales executive or sales manager");
            }
            rule.setAssignUser(user);
            rule.setAssignTeam(null);
        } else {
            rule.setAssignTeam(salesTeamRepository.findByIdAndTenantId(dto.getAssignTeamId(), tenantId)
                    .orElseThrow(() -> ApiException.badRequest("Team must belong to your tenant")));
            rule.setAssignUser(null);
        }
    }

    private LeadAssignmentRule findRule(User currentUser, Long ruleId) {
        return ruleRepository.findByIdAndTenantId(ruleId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Assignment rule not found"));
    }

    private LeadAssignmentRuleResDto toDto(LeadAssignmentRule r) {
        return new LeadAssignmentRuleResDto(r.getId(), r.getName(), r.getPriority(), r.isActive(),
                r.getMatchSource() != null ? r.getMatchSource().getDbValue() : null,
                r.getMatchIndustry() != null ? r.getMatchIndustry().getDbValue() : null,
                r.getAssignUser() != null ? r.getAssignUser().getId() : null,
                r.getAssignUser() != null ? r.getAssignUser().getFullName() : null,
                r.getAssignTeam() != null ? r.getAssignTeam().getId() : null,
                r.getAssignTeam() != null ? r.getAssignTeam().getName() : null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static LeadSource parseSource(String raw) {
        try {
            return LeadSource.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown lead source: " + raw);
        }
    }

    private static IndustryType parseIndustry(String raw) {
        try {
            return IndustryType.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown industry: " + raw);
        }
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Assignment rules are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
