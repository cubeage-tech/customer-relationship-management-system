package com.company.crm.quotation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuotationCustomerStatusReqDto {

    /** One of: viewed | approved | rejected | expired (recorded on the customer's behalf — no self-service portal yet). */
    @NotBlank
    private String status;
}
