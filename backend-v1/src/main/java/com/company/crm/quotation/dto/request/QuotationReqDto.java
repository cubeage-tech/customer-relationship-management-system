package com.company.crm.quotation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class QuotationReqDto {

    @NotNull
    private Long customerId;

    /** Optional — a quotation can exist without being tied to a specific pipeline deal. */
    private Long opportunityId;

    private LocalDate validUntil;

    private String notes;

    /** Salesperson this quotation is assigned to. Defaults to the creator when omitted. */
    private Long ownerId;

    @NotEmpty(message = "A quotation needs at least one line item")
    @Valid
    private List<QuotationLineItemReqDto> lineItems;
}
