package com.company.crm.common.audit;

/** BR-1/VAL-7: how a stage skipped by explicit override reads in the audit trail. */
public final class StageOverrides {

    private StageOverrides() {
    }

    public static String details(String from, String to, int skipped, String reason) {
        String details = "stage " + from + " → " + to + ", skipping " + skipped + " stage" + (skipped == 1 ? "" : "s");
        return reason == null || reason.isBlank() ? details + " (no reason given)" : details + ". Reason: " + reason.trim();
    }
}
