package com.company.crm.subscription.dto.response;

/** Usage against each plan limit. A null limit means unlimited. */
public record UsageResDto(Item users, Item customers, Item campaigns) {

    public record Item(Integer limit, long used) {}
}
