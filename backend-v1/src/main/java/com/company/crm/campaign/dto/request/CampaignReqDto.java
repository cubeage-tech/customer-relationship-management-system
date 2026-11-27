package com.company.crm.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /** FR-7.2/7.1c: the segment an email/SMS campaign is sent to (none = activate only, no sending). */
    private Long segmentId;

    /** FR-7.1c: email subject. */
    @Size(max = 200)
    private String messageSubject;

    /** FR-7.1c: the message; links in it are tracked (FR-7.4). */
    @Size(max = 20000)
    private String messageBody;
}
