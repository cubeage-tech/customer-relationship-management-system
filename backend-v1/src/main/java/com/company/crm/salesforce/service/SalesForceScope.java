package com.company.crm.salesforce.service;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Module 8: whose targets, activities, attendance and commission a user may see. The admin and the
 * executive owner see everyone; a sales manager sees the members of the teams they manage (the
 * whole tenant when they manage none — same rule as the CRM data scope); everyone else only themself.
 */
@Component
@RequiredArgsConstructor
public class SalesForceScope {

    private static final Set<RoleType> SEE_ALL = Set.of(RoleType.ADMIN, RoleType.EXECUTIVE_OWNER);

    private final UserRepository userRepository;
    private final SalesTeamRepository salesTeamRepository;
    private final DataScopeResolver dataScopeResolver;

    public List<User> visibleUsers(User current) {
        Long tenantId = tenantId(current);
        if (seesWholeTenant(current)) {
            return userRepository.findByTenantId(tenantId);
        }
        if (current.getRole().getName() == RoleType.SALES_MANAGER) {
            DataScope scope = dataScopeResolver.resolve(current);
            return userRepository.findByTenantIdAndIdIn(tenantId, scope.ownerIds());
        }
        return List.of(userRepository.findById(current.getId()).orElseThrow());
    }

    public boolean canSee(User current, Long userId) {
        return Objects.equals(current.getId(), userId) || seesWholeTenant(current)
                || (current.getRole().getName() == RoleType.SALES_MANAGER && dataScopeResolver.resolve(current).allows(userId));
    }

    /** Teams whose team-level figures the user may see. */
    public List<SalesTeam> visibleTeams(User current) {
        List<SalesTeam> all = salesTeamRepository.findByTenantIdOrderByNameAsc(tenantId(current));
        if (seesWholeTenant(current)) {
            return all;
        }
        if (current.getRole().getName() == RoleType.SALES_MANAGER) {
            List<Long> managed = dataScopeResolver.managedTeamIds(current);
            return all.stream().filter(t -> managed.contains(t.getId())).toList();
        }
        return all.stream().filter(t -> t.getId().equals(current.getTeamId())).toList();
    }

    /** Admin and the executive owner — plus a sales manager who manages no team (tenant-wide scope). */
    public boolean seesWholeTenant(User current) {
        RoleType role = current.getRole().getName();
        return SEE_ALL.contains(role) || (role == RoleType.SALES_MANAGER && dataScopeResolver.resolve(current).isTenantWide());
    }

    public Long tenantId(User current) {
        if (current.getTenant() == null) {
            throw ApiException.forbidden("Sales force data is scoped to a tenant");
        }
        return current.getTenant().getId();
    }
}
