package com.company.crm.salesforce.service;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.salesforce.dto.SalesForceDtos.TargetReq;
import com.company.crm.salesforce.dto.SalesForceDtos.TargetRes;
import com.company.crm.salesforce.entity.SalesTarget;
import com.company.crm.salesforce.repository.SalesForceQuery;
import com.company.crm.salesforce.repository.SalesTargetRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FR-8.1/FR-8.2: sales targets per salesperson or team for a period, with attainment. Actuals are
 * computed live from won deals (revenue = their value, units = how many), so attainment moves the
 * moment a deal is marked Won (AC-8.1). The admin sets any target; a sales manager sets targets for
 * the people and teams they manage.
 */
@Service
@RequiredArgsConstructor
public class SalesTargetService {

    private static final Set<RoleType> SALES_ROLES = Set.of(RoleType.SALES_EXECUTIVE, RoleType.SALES_MANAGER);

    private final SalesTargetRepository targetRepository;
    private final SalesForceQuery query;
    private final SalesForceScope scope;
    private final UserRepository userRepository;
    private final SalesTeamRepository salesTeamRepository;

    /** Targets the caller may see (their own, their team's, or those they manage), newest period first. */
    @Transactional(readOnly = true)
    public List<TargetRes> list(User currentUser) {
        Long tenantId = scope.tenantId(currentUser);
        List<Long> teamIds = scope.visibleTeams(currentUser).stream().map(SalesTeam::getId).toList();
        return targetRepository.findByTenantIdOrderByPeriodStartDescIdDesc(tenantId).stream()
                .filter(t -> t.getUser() != null ? scope.canSee(currentUser, t.getUser().getId()) : teamIds.contains(t.getTeam().getId()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TargetRes create(User currentUser, TargetReq dto) {
        SalesTarget target = new SalesTarget();
        target.setTenant(currentUser.getTenant());
        target.setCreatedBy(currentUser);
        applyFields(currentUser, target, dto);
        return toDto(targetRepository.save(target));
    }

    @Transactional
    public TargetRes update(User currentUser, Long targetId, TargetReq dto) {
        SalesTarget target = findManageable(currentUser, targetId);
        applyFields(currentUser, target, dto);
        return toDto(targetRepository.save(target));
    }

    @Transactional
    public void delete(User currentUser, Long targetId) {
        targetRepository.delete(findManageable(currentUser, targetId));
    }

    // ==================== Helpers ====================

    private void applyFields(User currentUser, SalesTarget t, TargetReq dto) {
        Long tenantId = scope.tenantId(currentUser);
        if ((dto.getUserId() == null) == (dto.getTeamId() == null)) {
            throw ApiException.badRequest("Set the target for either a salesperson or a team");
        }
        String metric = dto.getMetric().trim().toLowerCase(Locale.ROOT);
        if (!SalesTarget.REVENUE.equals(metric) && !SalesTarget.UNITS.equals(metric)) {
            throw ApiException.badRequest("The target must be revenue or units");
        }
        if (dto.getPeriodEnd().isBefore(dto.getPeriodStart())) {
            throw ApiException.badRequest("The period must end on or after its start");
        }
        if (dto.getUserId() != null) {
            User user = userRepository.findByIdAndTenantId(dto.getUserId(), tenantId)
                    .orElseThrow(() -> ApiException.badRequest("Salesperson must belong to your tenant"));
            if (!SALES_ROLES.contains(user.getRole().getName())) {
                throw ApiException.badRequest("Targets can be set for sales executives and sales managers");
            }
            if (!canManageUser(currentUser, user.getId())) {
                throw ApiException.forbidden("You can only set targets for the people you manage");
            }
            t.setUser(user);
            t.setTeam(null);
        } else {
            SalesTeam team = salesTeamRepository.findByIdAndTenantId(dto.getTeamId(), tenantId)
                    .orElseThrow(() -> ApiException.badRequest("Team must belong to your tenant"));
            if (scope.visibleTeams(currentUser).stream().noneMatch(v -> v.getId().equals(team.getId()))) {
                throw ApiException.forbidden("You can only set targets for the teams you manage");
            }
            t.setTeam(team);
            t.setUser(null);
        }
        t.setMetric(metric);
        t.setTargetValue(SalesTarget.UNITS.equals(metric) ? dto.getTargetValue().setScale(0, RoundingMode.CEILING) : dto.getTargetValue());
        t.setPeriodStart(dto.getPeriodStart());
        t.setPeriodEnd(dto.getPeriodEnd());
    }

    private SalesTarget findManageable(User currentUser, Long targetId) {
        SalesTarget t = targetRepository.findByIdAndTenantId(targetId, scope.tenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Target not found"));
        boolean manageable = t.getUser() != null
                ? canManageUser(currentUser, t.getUser().getId())
                : scope.visibleTeams(currentUser).stream().anyMatch(v -> v.getId().equals(t.getTeam().getId()));
        if (!manageable) {
            throw ApiException.forbidden("You can only change targets for the people and teams you manage");
        }
        return t;
    }

    /** The admin manages anyone's target; a sales manager the people they manage, but not their own. */
    private boolean canManageUser(User currentUser, Long userId) {
        if (currentUser.getRole().getName() == RoleType.ADMIN) {
            return true;
        }
        return !userId.equals(currentUser.getId()) && scope.canSee(currentUser, userId);
    }

    private TargetRes toDto(SalesTarget t) {
        SalesForceQuery.WonTotals won = query.wonDeals(t.getTenant().getId(),
                t.getUser() != null ? t.getUser().getId() : null, t.getTeam() != null ? t.getTeam().getId() : null,
                t.getPeriodStart().atStartOfDay(), t.getPeriodEnd().plusDays(1).atStartOfDay());
        BigDecimal actual = SalesTarget.UNITS.equals(t.getMetric())
                ? BigDecimal.valueOf(won.count()) : won.value().setScale(2, RoundingMode.HALF_UP);
        double percent = actual.multiply(BigDecimal.valueOf(100)).divide(t.getTargetValue(), 1, RoundingMode.HALF_UP).doubleValue();
        return new TargetRes(t.getId(),
                t.getUser() != null ? t.getUser().getId() : null, t.getUser() != null ? t.getUser().getFullName() : null,
                t.getTeam() != null ? t.getTeam().getId() : null, t.getTeam() != null ? t.getTeam().getName() : null,
                t.getMetric(), t.getTargetValue(), t.getPeriodStart(), t.getPeriodEnd(), actual, won.count(), percent);
    }
}
