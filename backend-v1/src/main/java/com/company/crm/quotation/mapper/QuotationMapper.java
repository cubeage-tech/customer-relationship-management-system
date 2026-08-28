package com.company.crm.quotation.mapper;

import com.company.crm.quotation.dto.response.QuotationLineItemResDto;
import com.company.crm.quotation.dto.response.QuotationResDto;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.entity.QuotationLineItem;
import org.springframework.stereotype.Component;

@Component
public class QuotationMapper {

    public QuotationResDto toDto(Quotation quotation) {
        return new QuotationResDto(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getCustomer().getId(),
                quotation.getCustomer().getCompanyName(),
                quotation.getOpportunity() != null ? quotation.getOpportunity().getId() : null,
                quotation.getStatus().getDbValue(),
                quotation.getDiscountApprovalStatus().getDbValue(),
                quotation.getDiscountReviewNote(),
                quotation.getValidUntil(),
                quotation.getNotes(),
                quotation.getOwner() != null ? quotation.getOwner().getId() : null,
                quotation.getOwner() != null ? quotation.getOwner().getFullName() : null,
                quotation.getLineItems().stream().map(this::toLineItemDto).toList(),
                quotation.getSubtotal(),
                quotation.getGrandTotal(),
                quotation.getCreatedAt()
        );
    }

    public QuotationLineItemResDto toLineItemDto(QuotationLineItem item) {
        return new QuotationLineItemResDto(
                item.getId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountPercent(),
                item.getLineTotal()
        );
    }
}
