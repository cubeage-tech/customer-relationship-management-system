package com.company.crm.quotation.scheduler;

import com.company.crm.quotation.service.QuotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FR-4.5: quotations sent to a customer (pending or viewed) expire once their valid-until date has
 * passed. The customer's view link applies the same rule on open, so it never shows a stale offer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuotationExpiryJob {

    private final QuotationService quotationService;

    @Scheduled(cron = "${app.quotation.expiry-cron:0 5 * * * *}")
    public void expireOverdue() {
        int expired = quotationService.expireOverdue();
        if (expired > 0) {
            log.info("Quotation expiry: {} quotation(s) moved to expired", expired);
        }
    }
}
