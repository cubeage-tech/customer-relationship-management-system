package com.company.crm.quotation.mapper;

import com.company.crm.quotation.dto.response.PublicQuotationDto;
import com.company.crm.quotation.dto.response.QuotationLineItemResDto;
import com.company.crm.quotation.dto.response.QuotationResDto;
import com.company.crm.quotation.entity.Quotation;
import com.company.crm.quotation.entity.QuotationLineItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class QuotationMapper {

    @Value("${app.frontend.url}")
    private String frontendUrl;

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
                quotation.getCreatedAt(),
                viewUrl(quotation),
                quotation.getViewedAt(),
                quotation.getEstimatedCost(),
                margin(quotation),
                marginPercent(quotation),
                quotation.getLinesWithoutCost(),
                quotation.getCustomerResponseComment(),
                quotation.getCustomerRespondedAt()
        );
    }

    /** FR-4.8: grand total minus estimated cost, or null when the cost is unknown. */
    private static BigDecimal margin(Quotation quotation) {
        BigDecimal cost = quotation.getEstimatedCost();
        return cost != null ? quotation.getGrandTotal().subtract(cost) : null;
    }

    private static BigDecimal marginPercent(Quotation quotation) {
        BigDecimal margin = margin(quotation);
        BigDecimal total = quotation.getGrandTotal();
        if (margin == null || total.signum() == 0) {
            return null;
        }
        return margin.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** FR-4.5: the customer-facing link for a sent quotation (frontend route /q/:token). */
    public String viewUrl(Quotation quotation) {
        return quotation.getViewToken() != null ? frontendUrl + "/q/" + quotation.getViewToken() : null;
    }

    public QuotationLineItemResDto toLineItemDto(QuotationLineItem item) {
        return new QuotationLineItemResDto(
                item.getId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountPercent(),
                item.getLineTotal(),
                item.getUnitCost()
        );
    }

    /** FR-4.5/FR-4.8: a line as the customer sees it — no cost. */
    public PublicQuotationDto.LineItem toPublicLineItem(QuotationLineItem item) {
        return new PublicQuotationDto.LineItem(item.getId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice(), item.getDiscountPercent(), item.getLineTotal());
    }
}
