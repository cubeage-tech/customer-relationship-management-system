package com.company.crm.salesforce.service;

import com.company.crm.common.audit.AuditService;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.sales.entity.Opportunity;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.salesforce.dto.SalesForceDtos.CommissionEntryRes;
import com.company.crm.salesforce.dto.SalesForceDtos.CommissionRuleReq;
import com.company.crm.salesforce.dto.SalesForceDtos.CommissionRuleRes;
import com.company.crm.salesforce.entity.CommissionEntry;
import com.company.crm.salesforce.entity.CommissionRule;
import com.company.crm.salesforce.repository.CommissionEntryRepository;
import com.company.crm.salesforce.repository.CommissionRuleRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * FR-8.5/AC-8.2: commission from configurable rules. When a deal is marked Won, the first active
 * rule matching its owner (a named salesperson, their team, or everyone) and deal value records a
 * pending commission of rate% × deal value. The admin or finance approves it and marks it paid.
 * Re-opening the deal voids a pending/approved commission; winning it again recalculates it.
 */
@Service
@RequiredArgsConstructor
public class CommissionService {

    private static final Set<RoleType> FINANCE = Set.of(RoleType.ADMIN, RoleType.FINANCE_APPROVER);

    private final CommissionRuleRepository ruleRepository;
    private final AuditService auditService;
    private final CommissionEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final SalesTeamRepository salesTeamRepository;
    private final SalesForceScope scope;

    // ==================== AC-8.2: recording ====================

    /** Called by OpportunityService in the stage-change transaction. */
    @Transactional
    public void onStageChanged(Opportunity opportunity, String previousStage) {
        boolean nowWon = PipelineStageService.WON.equals(opportunity.getStage());
        boolean wasWon = PipelineStageService.WON.equals(previousStage);
        if (nowWon && !wasWon) {
            record(opportunity);
        } else if (wasWon && !nowWon) {
            entryRepository.findByOpportunityId(opportunity.getId()).ifPresent(e -> {
                if (CommissionEntry.PENDING.equals(e.getStatus()) || CommissionEntry.APPROVED.equals(e.getStatus())) {
                    e.setStatus(CommissionEntry.VOID);
                    entryRepository.save(e);
                    auditService.recordSystem(opportunity.getTenant().getId(), AuditAction.COMMISSION_VOIDED, AUDIT_ENTITY, e.getId(),
                            "opportunity=" + opportunity.getId() + ", amount=" + e.getAmount() + " (deal no longer won)");
                }
            });
        }
    }

    /** A won deal's value or owner was edited: a still-pending commission follows it. */
    @Transactional
    public void onWonDealEdited(Opportunity opportunity) {
        if (PipelineStageService.WON.equals(opportunity.getStage())) {
            entryRepository.findByOpportunityId(opportunity.getId())
                    .filter(e -> CommissionEntry.PENDING.equals(e.getStatus()))
                    .ifPresent(e -> record(opportunity));
        }
    }

    private void record(Opportunity o) {
        Optional<CommissionEntry> existing = entryRepository.findByOpportunityId(o.getId());
        if (existing.isPresent() && (CommissionEntry.PAID.equals(existing.get().getStatus())
                || CommissionEntry.APPROVED.equals(existing.get().getStatus()))) {
            return; // already settled for this deal
        }
        User owner = o.getOwner();
        BigDecimal value = o.getDealValue() != null ? o.getDealValue() : BigDecimal.ZERO;
        Optional<CommissionRule> rule = owner == null ? Optional.empty()
                : ruleRepository.findByTenantIdOrderByPriorityAscIdAsc(o.getTenant().getId()).stream()
                        .filter(r -> r.matches(owner, value)).findFirst();
        if (rule.isEmpty()) {
            existing.ifPresent(e -> {
                e.setStatus(CommissionEntry.VOID);
                entryRepository.save(e);
            });
            return;
        }
        CommissionEntry e = existing.orElseGet(CommissionEntry::new);
        e.setTenant(o.getTenant());
        e.setOpportunity(o);
        e.setUser(owner);
        e.setRule(rule.get());
        e.setRuleName(rule.get().getName());
        e.setDealValue(value);
        e.setRatePercent(rule.get().getRatePercent());
        e.setAmount(value.multiply(rule.get().getRatePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        e.setStatus(CommissionEntry.PENDING);
        e.setWonAt(o.getStageChangedAt() != null ? o.getStageChangedAt() : LocalDateTime.now());
        e.setApprovedBy(null);
        e.setApprovedAt(null);
        e.setPaidAt(null);
        entryRepository.save(e);
        auditService.recordSystem(o.getTenant().getId(), AuditAction.COMMISSION_RECORDED, AUDIT_ENTITY, e.getId(),
                "opportunity=" + o.getId() + ", user=" + owner.getFullName() + ", rule=" + e.getRuleName()
                        + ", dealValue=" + value + ", amount=" + e.getAmount());
    }

    private static final String AUDIT_ENTITY = "commission";

    // ==================== Entries ====================

    /** Commissions the caller may see: finance/admin/executive all, a manager their team's, a salesperson their own. */
    @Transactional(readOnly = true)
    public List<CommissionEntryRes> entries(User currentUser, String status) {
        boolean all = FINANCE.contains(currentUser.getRole().getName()) || scope.seesWholeTenant(currentUser);
        return entryRepository.findByTenantIdOrderByWonAtDesc(scope.tenantId(currentUser)).stream()
                .filter(e -> all || scope.canSee(currentUser, e.getUser().getId()))
                .filter(e -> status == null || status.isBlank() || e.getStatus().equals(status))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CommissionEntryRes approve(User currentUser, Long entryId) {
        CommissionEntry e = findEntry(currentUser, entryId);
        if (!CommissionEntry.PENDING.equals(e.getStatus())) {
            throw ApiException.badRequest("Only a pending commission can be approved");
        }
        e.setStatus(CommissionEntry.APPROVED);
        e.setApprovedBy(currentUser);
        e.setApprovedAt(LocalDateTime.now());
        auditService.record(currentUser, AuditAction.COMMISSION_APPROVED, AUDIT_ENTITY, e.getId(), "amount=" + e.getAmount());
        return toDto(entryRepository.save(e));
    }

    @Transactional
    public CommissionEntryRes markPaid(User currentUser, Long entryId) {
        CommissionEntry e = findEntry(currentUser, entryId);
        if (!CommissionEntry.APPROVED.equals(e.getStatus())) {
            throw ApiException.badRequest("Approve the commission before marking it paid");
        }
        e.setStatus(CommissionEntry.PAID);
        e.setPaidAt(LocalDateTime.now());
        auditService.record(currentUser, AuditAction.COMMISSION_PAID, AUDIT_ENTITY, e.getId(), "amount=" + e.getAmount());
        return toDto(entryRepository.save(e));
    }

    // ==================== Rules (admin) ====================

    @Transactional(readOnly = true)
    public List<CommissionRuleRes> rules(User currentUser) {
        return ruleRepository.findByTenantIdOrderByPriorityAscIdAsc(scope.tenantId(currentUser)).stream().map(this::toDto).toList();
    }

    @Transactional
    public CommissionRuleRes createRule(User currentUser, CommissionRuleReq dto) {
        CommissionRule r = new CommissionRule();
        r.setTenant(currentUser.getTenant());
        applyFields(currentUser, r, dto);
        CommissionRule saved = ruleRepository.save(r);
        auditService.record(currentUser, AuditAction.COMMISSION_RULE_CREATED, "commission_rule", saved.getId(), ruleSummary(saved));
        return toDto(saved);
    }

    @Transactional
    public CommissionRuleRes updateRule(User currentUser, Long ruleId, CommissionRuleReq dto) {
        CommissionRule r = findRule(currentUser, ruleId);
        applyFields(currentUser, r, dto);
        CommissionRule saved = ruleRepository.save(r);
        auditService.record(currentUser, AuditAction.COMMISSION_RULE_UPDATED, "commission_rule", saved.getId(), ruleSummary(saved));
        return toDto(saved);
    }

    /** Recorded commissions keep the rule's name and rate. */
    @Transactional
    public void deleteRule(User currentUser, Long ruleId) {
        CommissionRule r = findRule(currentUser, ruleId);
        auditService.record(currentUser, AuditAction.COMMISSION_RULE_DELETED, "commission_rule", r.getId(), ruleSummary(r));
        ruleRepository.delete(r);
    }

    private static String ruleSummary(CommissionRule r) {
        return "name=" + r.getName() + ", rate=" + r.getRatePercent() + "%, priority=" + r.getPriority();
    }

    // ==================== Helpers ====================

    private void applyFields(User currentUser, CommissionRule r, CommissionRuleReq dto) {
        Long tenantId = scope.tenantId(currentUser);
        if (dto.getUserId() != null && dto.getTeamId() != null) {
            throw ApiException.badRequest("A rule is for one salesperson, one team, or everyone — not both");
        }
        r.setName(dto.getName().trim());
        r.setPriority(dto.getPriority() != null ? dto.getPriority() : 100);
        r.setActive(dto.getActive() == null || dto.getActive());
        r.setRatePercent(dto.getRatePercent());
        r.setMinDealValue(dto.getMinDealValue());
        r.setUser(dto.getUserId() == null ? null : userRepository.findByIdAndTenantId(dto.getUserId(), tenantId)
                .orElseThrow(() -> ApiException.badRequest("Salesperson must belong to your tenant")));
        r.setTeam(dto.getTeamId() == null ? null : salesTeamRepository.findByIdAndTenantId(dto.getTeamId(), tenantId)
                .orElseThrow(() -> ApiException.badRequest("Team must belong to your tenant")));
    }

    private CommissionRule findRule(User currentUser, Long ruleId) {
        return ruleRepository.findByIdAndTenantId(ruleId, scope.tenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Commission rule not found"));
    }

    private CommissionEntry findEntry(User currentUser, Long entryId) {
        return entryRepository.findByIdAndTenantId(entryId, scope.tenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Commission not found"));
    }

    private CommissionRuleRes toDto(CommissionRule r) {
        return new CommissionRuleRes(r.getId(), r.getName(), r.getPriority(), r.isActive(), r.getRatePercent(),
                r.getUser() != null ? r.getUser().getId() : null, r.getUser() != null ? r.getUser().getFullName() : null,
                r.getTeam() != null ? r.getTeam().getId() : null, r.getTeam() != null ? r.getTeam().getName() : null,
                r.getMinDealValue());
    }

    private CommissionEntryRes toDto(CommissionEntry e) {
        Opportunity o = e.getOpportunity();
        return new CommissionEntryRes(e.getId(), o.getId(), o.getCustomer().getCompanyName(), o.getProductService(),
                e.getUser().getId(), e.getUser().getFullName(), e.getRuleName(), e.getDealValue(), e.getRatePercent(),
                e.getAmount(), e.getStatus(), e.getWonAt(), e.getApprovedAt(), e.getPaidAt());
    }
}
