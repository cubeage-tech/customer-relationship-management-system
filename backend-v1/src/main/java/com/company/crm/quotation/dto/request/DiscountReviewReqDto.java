package com.company.crm.quotation.dto.request;

import lombok.Getter;
import lombok.Setter;

/** Body for rejecting a discount (approving needs no payload). */
@Getter
@Setter
public class DiscountReviewReqDto {
    private String reason;
}
