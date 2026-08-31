package com.company.crm.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CampaignReqDto {

    @NotBlank
    private String name;

    private String description;

    /** One of the CampaignChannel db values, e.g. "email", "social_media". */
    @NotBlank
    private String channel;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal budget;

    private BigDecimal actualCost;

    /** Marketing staffer running this campaign. Defaults to the creator when omitted. */
    private Long ownerId;
}
