package com.company.crm.quotation.service;

import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.notification.service.CustomerNotifier;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.event.QuotationSentEvent;
import com.company.crm.quotation.mapper.QuotationMapper;
import com.company.crm.quotation.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * FR-4.5: once a quotation is sent (after commit), email the customer the tracked view link.
 * Every attempt — sent, failed, or skipped for a missing address — is logged on the customer's
 * communication timeline.
 */
@Service
@RequiredArgsConstructor
public class QuotationLinkEmailer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final QuotationRepository quotationRepository;
    private final QuotationMapper quotationMapper;
    private final CustomerNotifier customerNotifier;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQuotationSent(QuotationSentEvent event) {
        Quotation quotation = quotationRepository.findByIdAndTenantId(event.quotationId(), event.tenantId()).orElse(null);
        if (quotation == null || quotation.getViewToken() == null) {
            return;
        }
        String company = quotation.getTenant().getCompanyName();
        String subject = "Quotation " + quotation.getQuotationNumber() + " from " + company;
        String body = "Dear " + quotation.getCustomer().getCompanyName() + ",\n\n"
                + company + " has sent you quotation " + quotation.getQuotationNumber()
                + String.format(" for Rs %,.2f", quotation.getGrandTotal())
                + (quotation.getValidUntil() != null ? ", valid until " + DATE.format(quotation.getValidUntil()) : "") + ".\n\n"
                + "View or download it here:\n" + quotationMapper.viewUrl(quotation) + "\n\n"
                + "Reply to this email with any questions.\n\n"
                + "Regards,\n" + (quotation.getOwner() != null ? quotation.getOwner().getFullName() + "\n" : "") + company;
        // NOT-6: unless the tenant switched this email off; NOT-8: logged.
        customerNotifier.email(NotificationEvent.QUOTATION_SENT, quotation.getTenant(), quotation.getCustomer(),
                customerEmail(quotation.getCustomer()), subject, body, false);
    }

    /** The customer's own address, else the first contact person with one. */
    private static String customerEmail(Customer customer) {
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            return customer.getEmail();
        }
        return customer.getContacts().stream()
                .map(CustomerContact::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst().orElse(null);
    }
}
