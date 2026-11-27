package com.company.crm.quotation.service;

import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.event.QuotationRespondedEvent;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/** NAV-2: tells the quotation's owner (or its creator) that the customer answered it. */
@Service
@RequiredArgsConstructor
public class QuotationResponseNotifier {

    private final QuotationRepository quotationRepository;
    private final StaffNotifier staffNotifier;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onResponded(QuotationRespondedEvent event) {
        Quotation q = quotationRepository.findByIdAndTenantId(event.quotationId(), event.tenantId()).orElse(null);
        if (q == null) {
            return;
        }
        User owner = q.getOwner() != null ? q.getOwner() : q.getCreatedBy();
        if (owner == null) {
            return;
        }
        String comment = q.getCustomerResponseComment();
        staffNotifier.notify(event.tenantId(), NotificationEvent.QUOTATION_RESPONSE, List.of(owner),
                q.getCustomer().getCompanyName() + (event.accepted() ? " accepted " : " declined ") + q.getQuotationNumber(),
                comment != null ? "\"" + comment + "\"" : (event.accepted() ? "Accepted from the quotation link." : "Declined from the quotation link."),
                "quotation", q.getId(), null, "/quotations/" + q.getId());
    }
}
