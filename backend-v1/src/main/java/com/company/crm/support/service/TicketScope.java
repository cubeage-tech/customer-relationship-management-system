package com.company.crm.support.service;

import com.company.crm.common.exception.ApiException;

/** Which tickets a service agent lists (the {@code scope} query param). Ignored for other roles. */
public enum TicketScope {
    /** Assigned to me. */
    MINE,
    /** Unassigned — available to claim. */
    QUEUE,
    /** Mine plus the unassigned queue (default for agents). */
    ALL;

    public static TicketScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown scope: " + raw + " (use mine or queue)");
        }
    }
}
