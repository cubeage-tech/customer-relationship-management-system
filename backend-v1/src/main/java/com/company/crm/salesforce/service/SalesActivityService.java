package com.company.crm.salesforce.service;

import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.salesforce.dto.SalesForceDtos.ActivitySummary;
import com.company.crm.salesforce.dto.SalesForceDtos.VisitReq;
import com.company.crm.salesforce.dto.SalesForceDtos.VisitRes;
import com.company.crm.salesforce.entity.SalesActivity;
import com.company.crm.salesforce.repository.SalesActivityRepository;
import com.company.crm.salesforce.repository.SalesForceQuery;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * FR-8.3: sales activities per salesperson. Calls (logged communications), meetings they organise
 * and quotations they send are counted from their own records; field visits are logged here.
 */
@Service
@RequiredArgsConstructor
public class SalesActivityService {

    private static final Set<RoleType> SALES_ROLES = Set.of(RoleType.SALES_EXECUTIVE, RoleType.SALES_MANAGER);

    private final SalesActivityRepository activityRepository;
    private final SalesForceQuery query;
    private final SalesForceScope scope;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final DataScopeResolver dataScopeResolver;

    @Transactional
    public VisitRes logVisit(User currentUser, VisitReq dto) {
        Long tenantId = scope.tenantId(currentUser);
        if (dto.getCustomerId() != null && dto.getLeadId() != null) {
            throw ApiException.badRequest("A visit is to a customer or a lead, not both");
        }
        if (dto.getOccurredAt() != null && dto.getOccurredAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw ApiException.badRequest("A visit can't be logged in the future");
        }
        if ((dto.getLatitude() == null) != (dto.getLongitude() == null)) {
            throw ApiException.badRequest("Give both latitude and longitude, or neither");
        }
        String clientRef = blankToNull(dto.getClientRef());
        if (clientRef != null) {
            Optional<SalesActivity> synced = activityRepository.findByUserIdAndClientRef(currentUser.getId(), clientRef);
            if (synced.isPresent()) {
                return toDto(synced.get()); // FR-M.6: the app retried a sync that already went through
            }
        }
        SalesActivity a = new SalesActivity();
        a.setTenant(currentUser.getTenant());
        a.setUser(currentUser);
        a.setType(SalesActivity.VISIT);
        if (dto.getCustomerId() != null) {
            Customer c = customerRepository.findByIdAndTenantId(dto.getCustomerId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Customer not found"));
            dataScopeResolver.assertCanAccess(currentUser, c.getOwner() != null ? c.getOwner().getId() : null, "customer");
            a.setCustomer(c);
        }
        if (dto.getLeadId() != null) {
            Lead l = leadRepository.findByIdAndTenantId(dto.getLeadId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Lead not found"));
            dataScopeResolver.assertCanAccess(currentUser, l.getOwner() != null ? l.getOwner().getId() : null, "lead");
            a.setLead(l);
        }
        a.setOccurredAt(dto.getOccurredAt() != null ? dto.getOccurredAt() : LocalDateTime.now());
        a.setNotes(dto.getNotes() != null && !dto.getNotes().isBlank() ? dto.getNotes().trim() : null);
        a.setLatitude(dto.getLatitude());
        a.setLongitude(dto.getLongitude());
        a.setPurpose(blankToNull(dto.getPurpose()));
        a.setOutcome(blankToNull(dto.getOutcome()));
        a.setClientRef(clientRef);
        return toDto(activityRepository.save(a));
    }

    /** Visits in [from, to] by the people the caller may see (optionally one of them). */
    @Transactional(readOnly = true)
    public List<VisitRes> visits(User currentUser, Long userId, LocalDate from, LocalDate to) {
        List<Long> users = userIds(currentUser, userId);
        return activityRepository.findByTenantIdAndUserIdInAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                        scope.tenantId(currentUser), users, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream().map(this::toDto).toList();
    }

    /** Per-salesperson counts over [from, to], most active first. */
    @Transactional(readOnly = true)
    public List<ActivitySummary> summary(User currentUser, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The period must end on or after its start");
        }
        Long tenantId = scope.tenantId(currentUser);
        List<User> people = scope.visibleUsers(currentUser).stream()
                .filter(u -> SALES_ROLES.contains(u.getRole().getName()) || u.getId().equals(currentUser.getId()))
                .toList();
        List<Long> ids = people.stream().map(User::getId).toList();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Map<Long, Long> calls = query.calls(tenantId, ids, start, end);
        Map<Long, Long> meetings = query.meetings(tenantId, ids, start, end);
        Map<Long, Long> quotations = query.quotationsSent(tenantId, ids, start, end);
        Map<Long, Long> visits = query.visits(tenantId, ids, start, end);
        return people.stream()
                .map(u -> {
                    long c = calls.getOrDefault(u.getId(), 0L);
                    long m = meetings.getOrDefault(u.getId(), 0L);
                    long q = quotations.getOrDefault(u.getId(), 0L);
                    long v = visits.getOrDefault(u.getId(), 0L);
                    return new ActivitySummary(u.getId(), u.getFullName(), u.getRole().getName().getDbValue(), c, m, q, v, c + m + q + v);
                })
                .sorted(Comparator.comparingLong(ActivitySummary::total).reversed().thenComparing(ActivitySummary::userName))
                .toList();
    }

    // ==================== Helpers ====================

    private List<Long> userIds(User currentUser, Long userId) {
        if (userId != null) {
            if (!scope.canSee(currentUser, userId)) {
                throw ApiException.forbidden("You can only see the activity of the people you manage");
            }
            return List.of(userId);
        }
        return scope.visibleUsers(currentUser).stream().map(User::getId).toList();
    }

    private VisitRes toDto(SalesActivity a) {
        return new VisitRes(a.getId(), a.getUser().getId(), a.getUser().getFullName(),
                a.getCustomer() != null ? a.getCustomer().getId() : null, a.getCustomer() != null ? a.getCustomer().getCompanyName() : null,
                a.getLead() != null ? a.getLead().getId() : null, a.getLead() != null ? a.getLead().getLeadName() : null,
                a.getOccurredAt(), a.getNotes(), a.getLatitude(), a.getLongitude(), a.getPurpose(), a.getOutcome());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
