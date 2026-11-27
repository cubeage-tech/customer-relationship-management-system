package com.company.crm.quotation.service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.event.DiscountApprovalRequestedEvent;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.rolepermission.RolePermissionService;
import com.company.crm.tenant.service.TenantSettingsService;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NOT-5: when a quotation's discount goes above the tenant's threshold, the people who can approve
 * it (admins, sales managers, finance approvers — unless the tenant switched that permission off for
 * their role, ROLE-8) are told, in-app and/or by email as the tenant chose (NOT-6).
 */
@Service
@RequiredArgsConstructor
public class DiscountApprovalNotifier {

    /** Approver roles and the permission that lets each approve (the admin always can). */
    private static final Map<RoleType, Set<String>> APPROVERS = Map.of(
            RoleType.ADMIN, Set.of(),
            RoleType.SALES_MANAGER, Set.of("quotations.approve"),
            RoleType.FINANCE_APPROVER, Set.of("quotations.approveDiscount"));

    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;
    private final RolePermissionService rolePermissions;
    private final TenantSettingsService tenantSettings;
    private final StaffNotifier staffNotifier;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDiscountApprovalRequested(DiscountApprovalRequestedEvent event) {
        Quotation q = quotationRepository.findByIdAndTenantId(event.quotationId(), event.tenantId()).orElse(null);
        if (q == null) {
            return;
        }
        List<User> approvers = userRepository.findByTenantId(event.tenantId()).stream()
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE && !u.getId().equals(event.requestedById()))
                .filter(u -> APPROVERS.containsKey(u.getRole().getName()))
                .filter(u -> {
                    Set<String> needed = APPROVERS.get(u.getRole().getName());
                    return needed.isEmpty() || !rolePermissions.revoked(event.tenantId(), u.getRole().getName()).containsAll(needed);
                })
                .toList();
        if (approvers.isEmpty()) {
            return;
        }
        String requester = q.getOwner() != null ? q.getOwner().getFullName() : q.getCreatedBy() != null ? q.getCreatedBy().getFullName() : null;
        String body = String.format("%s · discount up to %s%% (approval above %s%%) · total Rs %,.2f%s",
                q.getCustomer().getCompanyName(), q.getMaxDiscountPercent().stripTrailingZeros().toPlainString(),
                tenantSettings.discountApprovalThreshold(event.tenantId()).stripTrailingZeros().toPlainString(), q.getGrandTotal(),
                requester != null ? " · by " + requester : "");
        staffNotifier.notify(event.tenantId(), NotificationEvent.DISCOUNT_APPROVAL_REQUESTED, approvers,
                "Discount approval needed: " + q.getQuotationNumber(), body, "quotation", q.getId(), event.requestedById(),
                "/quotations/" + q.getId());
    }
}
