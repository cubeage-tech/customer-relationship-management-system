package com.company.crm.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** FR-5.3: switch one SMS alert event on/off and choose its recipient. */
@Getter
@Setter
public class SmsAlertRuleReqDto {

    /** quotation_sent | ticket_status_changed */
    @NotBlank
    private String event;

    private boolean enabled;

    /** customer | internal */
    @NotBlank
    private String recipient;
}
