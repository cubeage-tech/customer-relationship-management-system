package com.company.crm.common.scope;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.sales_team.repository.SalesTeamRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single place that decides how much of a tenant's owned records (customers, leads,
 * opportunities, quotations) a user may see:
 * <ul>
 *   <li>sales_executive — OWN: records they own</li>
 *   <li>sales_manager — TEAM: records owned by members of the teams they manage, plus their own.
 *       Falls back to TENANT (with a warning) when they manage no team, or when
 *       {@code app.features.sales-team-scope=false}</li>
 *   <li>everyone else (admin, marketing, finance, executive owner, service) — TENANT</li>
 * </ul>
 * Services must ask this class instead of checking roles themselves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataScopeResolver {

    private final SalesTeamRepository salesTeamRepository;
    private final UserRepository userRepository;

    @Value("${app.features.sales-team-scope:true}")
    private boolean salesTeamScopeEnabled;

    public DataScope resolve(User user) {
        RoleType role = user.getRole().getName();
        return switch (role) {
            case SALES_EXECUTIVE -> DataScope.own(user.getId());
            case SALES_MANAGER -> teamScope(user);
            default -> DataScope.tenant();
        };
    }

    /** Throws 403 unless a record owned by {@code ownerId} is inside the user's scope. */
    public void assertCanAccess(User user, Long ownerId, String recordLabel) {
        if (!resolve(user).allows(ownerId)) {
            throw ApiException.forbidden("You do not have access to this " + recordLabel);
        }
    }

    private DataScope teamScope(User manager) {
        if (!salesTeamScopeEnabled || manager.getTenant() == null) {
            return DataScope.tenant();
        }
        Long tenantId = manager.getTenant().getId();
        List<Long> teamIds = salesTeamRepository.findIdsManagedBy(tenantId, manager.getId());
        if (teamIds.isEmpty()) {
            log.warn("Sales manager userId={} manages no team; falling back to tenant-wide visibility", manager.getId());
            return DataScope.tenant();
        }
        Set<Long> owners = new HashSet<>(userRepository.findIdsByTenantIdAndTeamIdIn(tenantId, teamIds));
        owners.add(manager.getId());
        return DataScope.team(owners);
    }
}
