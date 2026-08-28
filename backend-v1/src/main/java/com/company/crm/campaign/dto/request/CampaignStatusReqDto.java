package com.company.crm.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampaignStatusReqDto {

    /** One of: draft | active | paused | completed | cancelled */
    @NotBlank
    private String status;
}
