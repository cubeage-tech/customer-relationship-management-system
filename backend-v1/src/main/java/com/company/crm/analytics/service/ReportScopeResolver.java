package com.company.crm.analytics.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FR-9.6: turns a report's filters (period, industry, team, user) into a {@link ReportScope}
 * intersected with the caller's data scope (AC-9.2) — a filter can narrow what they see, never widen it.
 */
@Component
@RequiredArgsConstructor
public class ReportScopeResolver {

    /** Longest period a report may cover. */
    private static final int MAX_DAYS = 3 * 366;

    private final DataScopeResolver dataScopeResolver;
    private final SalesForceScope salesForceScope;
    private final UserRepository userRepository;

    public ReportScope resolve(User user, LocalDate from, LocalDate to, String industry, Long teamId, Long userId) {
        Long tenantId = salesForceScope.tenantId(user);
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("The period must end on or after its start");
        }
        if (end.toEpochDay() - start.toEpochDay() > MAX_DAYS) {
            throw ApiException.badRequest("Reports can cover at most three years");
        }

        DataScope scope = dataScopeResolver.resolve(user);
        Set<Long> owners = scope.isTenantWide() ? null : new HashSet<>(scope.ownerIds());
        if (teamId != null) {
            if (salesForceScope.visibleTeams(user).stream().noneMatch(t -> t.getId().equals(teamId))) {
                throw ApiException.forbidden("You can't report on that team");
            }
            Set<Long> members = new HashSet<>(userRepository.findIdsByTenantIdAndTeamIdIn(tenantId, List.of(teamId)));
            owners = owners == null ? members : intersect(owners, members);
        }
        if (userId != null) {
            if (!salesForceScope.canSee(user, userId) && !scope.allows(userId)) {
                throw ApiException.forbidden("You can't report on that user");
            }
            owners = owners == null ? Set.of(userId) : intersect(owners, Set.of(userId));
        }
        String ind = industry == null || industry.isBlank() ? null : industry.trim();
        return new ReportScope(tenantId, owners, ind, start, end);
    }

    private static Set<Long> intersect(Set<Long> a, Set<Long> b) {
        Set<Long> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }
}
