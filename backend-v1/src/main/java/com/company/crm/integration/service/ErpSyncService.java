package com.company.crm.integration.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.integration.dto.IntegrationDtos.*;
import com.company.crm.quotation.entity.Product;
import com.company.crm.quotation.repository.ProductRepository;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.sales.repository.OpportunityRepository;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API-G12: the generic ERP sync. The ERP pulls what the CRM owns — customers, won deals, approved
 * quotations — changed since its last sync, and pushes what it owns — its customer codes and its
 * product price list. Every call is scoped to the tenant of the API key used.
 */
@Service
@RequiredArgsConstructor
public class ErpSyncService {

    /** Most rows returned per pull; the ERP pages with since = the last row's timestamp. */
    public static final int MAX_ROWS = 500;

    private final CustomerRepository customerRepository;
    private final OpportunityRepository opportunityRepository;
    private final QuotationRepository quotationRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ErpCustomer> customers(Long tenantId, LocalDateTime since) {
        return customerRepository.findByTenantId(tenantId).stream()
                .filter(c -> since == null || !c.getUpdatedAt().isBefore(since))
                .sorted(Comparator.comparing(Customer::getUpdatedAt).thenComparing(Customer::getId))
                .limit(MAX_ROWS)
                .map(c -> new ErpCustomer(c.getId(), c.getErpCode(), c.getCompanyName(), c.getIndustry(),
                        c.getStatus() != null ? c.getStatus().getDbValue() : null, c.getEmail(), c.getPhone(), c.getAddress(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ErpWonDeal> wonDeals(Long tenantId, LocalDateTime since) {
        return opportunityRepository.findByTenantId(tenantId).stream()
                .filter(o -> PipelineStageService.WON.equals(o.getStage()) && o.getStageChangedAt() != null)
                .filter(o -> since == null || !o.getStageChangedAt().isBefore(since))
                .sorted(Comparator.comparing(com.company.crm.sales.entity.Opportunity::getStageChangedAt))
                .limit(MAX_ROWS)
                .map(o -> new ErpWonDeal(o.getId(), o.getCustomer().getId(), o.getCustomer().getErpCode(), o.getCustomer().getCompanyName(),
                        o.getProductService(), o.getDealValue(), o.getStageChangedAt(), o.getOwner() != null ? o.getOwner().getFullName() : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ErpQuotation> approvedQuotations(Long tenantId, LocalDateTime since) {
        return quotationRepository.findByTenantId(tenantId).stream()
                .filter(q -> q.getStatus() == QuotationStatus.APPROVED && q.getCustomerApprovedAt() != null)
                .filter(q -> since == null || !q.getCustomerApprovedAt().isBefore(since))
                .sorted(Comparator.comparing(com.company.crm.quotation.entity.Quotation::getCustomerApprovedAt))
                .limit(MAX_ROWS)
                .map(q -> new ErpQuotation(q.getId(), q.getQuotationNumber(), q.getCustomer().getId(), q.getCustomer().getErpCode(),
                        q.getCustomer().getCompanyName(), q.getValidUntil(), q.getSubtotal(), q.getGrandTotal(), q.getCustomerApprovedAt(),
                        q.getLineItems().stream().map(l -> new ErpQuotationLine(l.getProductName(), l.getQuantity(), l.getUnitPrice(),
                                l.getDiscountPercent())).toList()))
                .toList();
    }

    /** Links a CRM customer to its ERP record. A code already used by another customer is a conflict. */
    @Transactional
    public ErpCustomer setCustomerCode(Long tenantId, Long customerId, String erpCode) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        String code = erpCode.trim();
        boolean taken = customerRepository.findByTenantId(tenantId).stream()
                .anyMatch(c -> code.equals(c.getErpCode()) && !c.getId().equals(customerId));
        if (taken) {
            throw ApiException.conflict("Another customer already has ERP code " + code);
        }
        String before = customer.getErpCode();
        customer.setErpCode(code);
        Customer saved = customerRepository.save(customer);
        auditService.recordSystem(tenantId, AuditAction.ERP_CUSTOMER_CODE_SET, "customer", customerId,
                "erpCode " + (before == null ? "none" : before) + " → " + code + " (ERP integration)");
        return new ErpCustomer(saved.getId(), saved.getErpCode(), saved.getCompanyName(), saved.getIndustry(),
                saved.getStatus() != null ? saved.getStatus().getDbValue() : null, saved.getEmail(), saved.getPhone(), saved.getAddress(),
                saved.getUpdatedAt());
    }

    /** Upserts the ERP's price list into CRM products by ERP code — the prices quotations use (FR-4.2). */
    @Transactional
    public ProductSyncRes syncProducts(Long tenantId, ProductSyncReq dto) {
        Map<String, Product> existing = productRepository.findByTenantId(tenantId).stream()
                .filter(p -> p.getErpCode() != null)
                .collect(Collectors.toMap(Product::getErpCode, Function.identity(), (a, b) -> a));
        long distinct = dto.getProducts().stream().map(r -> r.getErpCode().trim()).distinct().count();
        if (distinct != dto.getProducts().size()) {
            throw ApiException.badRequest("Each erpCode may appear only once per sync");
        }
        int created = 0;
        int updated = 0;
        for (ProductRow row : dto.getProducts()) {
            Product product = existing.get(row.getErpCode().trim());
            if (product == null) {
                product = new Product();
                product.setTenant(tenantRepository.getReferenceById(tenantId));
                product.setErpCode(row.getErpCode().trim());
                created++;
            } else {
                updated++;
            }
            product.setName(row.getName().trim());
            product.setDescription(row.getDescription());
            product.setUnitPrice(row.getUnitPrice());
            if (row.getCostPrice() != null) {
                product.setCostPrice(row.getCostPrice());
            }
            if (row.getActive() != null) {
                product.setActive(row.getActive());
            }
            productRepository.save(product);
        }
        auditService.recordSystem(tenantId, AuditAction.ERP_PRODUCTS_SYNCED, "product", null,
                "created=" + created + ", updated=" + updated + " (ERP integration)");
        return new ProductSyncRes(created, updated);
    }
}
