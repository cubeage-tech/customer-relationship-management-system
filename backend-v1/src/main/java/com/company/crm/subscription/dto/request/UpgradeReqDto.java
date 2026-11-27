package com.company.crm.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpgradeReqDto(
        @NotNull Long planId,
        /** monthly | annual */
        @NotBlank String billingCycle
) {}
