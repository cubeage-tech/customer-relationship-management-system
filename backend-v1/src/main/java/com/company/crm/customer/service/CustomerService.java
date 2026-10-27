package com.company.crm.customer.service;

import com.company.crm.common.pagination.PageRequestFactory;
import com.company.crm.common.pagination.PageResponse;
import com.company.crm.common.scope.ScopeSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.Map;
import com.company.crm.common.period.ReportingPeriods;
import com.company.crm.customer.dto.response.CustomerSummaryDto;
import com.company.crm.customer.repository.CustomerSummaryQuery;
import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.industry.service.IndustryService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScope;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.dto.request.CustomerContactReqDto;
import com.company.crm.customer.dto.request.CustomerReqDto;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.customer.dto.response.CustomerContactResDto;
import com.company.crm.customer.dto.response.CustomerPurchaseResDto;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.quotation.entity.QuotationLineItem;
import com.company.crm.quotation.repository.QuotationRepository;
import com.company.crm.sales.repository.OpportunityRepository;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.customer.mapper.CustomerMapper;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.subscription.service.PlanLimitService;
import com.company.crm.subscription.service.PlanLimitService.LimitedResource;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final String AUDIT_ENTITY = "customer";

    private final PageRequestFactory pageRequestFactory;
    private final CustomerRepository customerRepository;
    private final DataScopeResolver dataScopeResolver;
    private final CustomerSummaryQuery customerSummaryQuery;
    private final ReportingPeriods reportingPeriods;
    private final UserRepository userRepository;
    private final CustomerMapper customerMapper;
    private final PlanLimitService planLimitService;
    private final OpportunityRepository opportunityRepository;
    private final IndustryService industryService;
    private final AuditService auditService;
    private final QuotationRepository quotationRepository;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (contacts, owner, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<CustomerResDto> listCustomers(User currentUser, String industry, String status, String search) {
        // NFR-7.1b: filtered in the database rather than in memory.
        return customerRepository.findAll(filters(currentUser, industry, status, search), LIST_ORDER).stream()
                .map(customerMapper::toDto)
                .toList();
    }

    /** NFR-7.1b: one page of the same list (opt-in via page/size); newest first unless {@code sort} says otherwise. */
    @Transactional(readOnly = true)
    public PageResponse<CustomerResDto> listCustomersPage(User currentUser, String industry, String status, String search,
                                                          Integer page, Integer size, String sort) {
        Pageable pageable = pageRequestFactory.of(page, size, sort, SORTABLE_FIELDS, PAGE_ORDER);
        return PageResponse.of(customerRepository.findAll(filters(currentUser, industry, status, search), pageable), customerMapper::toDto);
    }

    private static final Sort LIST_ORDER = Sort.by(Sort.Direction.ASC, "id");
    private static final Sort PAGE_ORDER = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    private static final Map<String, String> SORTABLE_FIELDS = Map.of("createdAt", "createdAt", "companyName", "companyName",
            "industry", "industry", "status", "status");

    private Specification<Customer> filters(User currentUser, String industry, String status, String search) {
        Specification<Customer> spec = ScopeSpecifications.inTenant(requireTenantId(currentUser));
        return spec.and(ScopeSpecifications.ownedWithin(dataScopeResolver.resolve(currentUser)))
                .and(ScopeSpecifications.hasValue("industry", industry))
                .and(ScopeSpecifications.hasEnum("status", status, CustomerStatus.class, CustomerStatus::getDbValue))
                .and(ScopeSpecifications.contains("companyName", search));
    }

    @Transactional(readOnly = true)
    public CustomerResDto getCustomer(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertViewAccess(currentUser, customer);
        return customerMapper.toDto(customer);
    }

    /**
     * FR-1.3: purchase history, newest first. A purchase is a won deal (dated when it was marked
     * won) or a customer-approved quotation (dated when the approval was recorded). A quotation
     * whose deal is already won is the same purchase, so only the deal is listed.
     */
    @Transactional(readOnly = true)
    public List<CustomerPurchaseResDto> listPurchases(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertViewAccess(currentUser, customer);
        Long tenantId = requireTenantId(currentUser);

        List<CustomerPurchaseResDto> purchases = new ArrayList<>();
        opportunityRepository.findByTenantIdAndCustomerIdAndStage(tenantId, customerId, PipelineStageService.WON)
                .forEach(o -> purchases.add(new CustomerPurchaseResDto("opportunity", o.getId(), null, o.getProductService(),
                        o.getDealValue(), o.getStageChangedAt() != null ? o.getStageChangedAt() : o.getUpdatedAt())));
        quotationRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, QuotationStatus.APPROVED).stream()
                .filter(q -> q.getOpportunity() == null || !PipelineStageService.WON.equals(q.getOpportunity().getStage()))
                .forEach(q -> purchases.add(new CustomerPurchaseResDto("quotation", q.getId(), q.getQuotationNumber(),
                        q.getLineItems().stream().map(QuotationLineItem::getProductName).distinct().collect(Collectors.joining(", ")),
                        q.getGrandTotal(), q.getCustomerApprovedAt() != null ? q.getCustomerApprovedAt() : q.getUpdatedAt())));
        purchases.sort(Comparator.comparing(CustomerPurchaseResDto::purchasedAt).reversed());
        return purchases;
    }

    @Transactional
    public CustomerResDto createCustomer(User currentUser, CustomerReqDto dto) {
        requireTenantId(currentUser);
        planLimitService.assertCanAdd(currentUser.getTenant(), LimitedResource.CUSTOMERS);

        Customer customer = new Customer();
        customer.setTenant(currentUser.getTenant());
        customer.setCreatedBy(currentUser);
        applyFields(currentUser, customer, dto);
        Customer saved = customerRepository.save(customer);
        auditService.record(currentUser, AuditAction.CUSTOMER_CREATED, AUDIT_ENTITY, saved.getId(), "name=" + saved.getCompanyName());
        return customerMapper.toDto(saved);
    }

    @Transactional
    public CustomerResDto updateCustomer(User currentUser, Long customerId, CustomerReqDto dto) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);
        List<String> before = snapshot(customer);
        applyFields(currentUser, customer, dto);
        Customer saved = customerRepository.save(customer);
        // AC-1.2: every saved edit leaves an audit entry naming the fields that changed (not their values).
        List<String> changed = changedFields(before, snapshot(saved));
        if (!changed.isEmpty()) {
            auditService.record(currentUser, AuditAction.CUSTOMER_UPDATED, AUDIT_ENTITY, saved.getId(), "changed=" + String.join(",", changed));
        }
        return customerMapper.toDto(saved);
    }

    @Transactional
    public CustomerResDto archiveCustomer(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);
        customer.setStatus(CustomerStatus.ARCHIVED);
        auditService.record(currentUser, AuditAction.CUSTOMER_ARCHIVED, AUDIT_ENTITY, customer.getId(), null);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto restoreCustomer(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);
        customer.setStatus(CustomerStatus.ACTIVE);
        auditService.record(currentUser, AuditAction.CUSTOMER_RESTORED, AUDIT_ENTITY, customer.getId(), null);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto addContact(User currentUser, Long customerId, CustomerContactReqDto dto) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);

        CustomerContact contact = new CustomerContact();
        contact.setCustomer(customer);
        applyContactFields(contact, dto);
        customer.getContacts().add(contact);
        auditService.record(currentUser, AuditAction.CUSTOMER_CONTACT_ADDED, AUDIT_ENTITY, customer.getId(), "contact=" + contact.getName());

        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto updateContact(User currentUser, Long customerId, Long contactId, CustomerContactReqDto dto) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);

        CustomerContact contact = findContact(customer, contactId);
        applyContactFields(contact, dto);
        auditService.record(currentUser, AuditAction.CUSTOMER_CONTACT_UPDATED, AUDIT_ENTITY, customer.getId(), "contact=" + contact.getName());

        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto deleteContact(User currentUser, Long customerId, Long contactId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);

        CustomerContact contact = findContact(customer, contactId);
        customer.getContacts().remove(contact);
        auditService.record(currentUser, AuditAction.CUSTOMER_CONTACT_REMOVED, AUDIT_ENTITY, customer.getId(), "contact=" + contact.getName());

        return customerMapper.toDto(customerRepository.save(customer));
    }

    // ==================== Helpers ====================

    private void applyFields(User currentUser, Customer customer, CustomerReqDto dto) {
        customer.setCompanyName(dto.getCompanyName());
        // FR-1.6: a new or changed industry must be active in the tenant's list; an unchanged one is
        // kept even if it has since been deactivated.
        if (!java.util.Objects.equals(customer.getIndustry(), dto.getIndustry())) {
            industryService.requireActive(requireTenantId(currentUser), dto.getIndustry());
        }
        customer.setIndustry(dto.getIndustry());
        customer.setEmail(dto.getEmail());
        customer.setPhone(dto.getPhone());
        customer.setWebsite(dto.getWebsite());
        customer.setAddress(dto.getAddress());
        customer.setNotes(dto.getNotes());
        customer.setOwner(resolveOwner(currentUser, dto.getOwnerId()));
    }

    private void applyContactFields(CustomerContact contact, CustomerContactReqDto dto) {
        contact.setName(dto.getName());
        contact.setDesignation(dto.getDesignation());
        contact.setPhone(dto.getPhone());
        contact.setEmail(dto.getEmail());
    }

    private User resolveOwner(User currentUser, Long ownerId) {
        if (ownerId == null) {
            return currentUser;
        }
        User owner = userRepository.findByIdAndTenantId(ownerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.badRequest("Owner must belong to your tenant"));
        return owner;
    }

    /** AC-1.2: comparable view of the audited fields (owner by id). */
    private static List<String> snapshot(Customer c) {
        return java.util.Arrays.asList(c.getCompanyName(), c.getIndustry(), c.getEmail(), c.getPhone(), c.getWebsite(),
                c.getAddress(), c.getNotes(), c.getOwner() != null ? String.valueOf(c.getOwner().getId()) : null);
    }

    private static final List<String> AUDITED_FIELDS =
            List.of("companyName", "industry", "email", "phone", "website", "address", "notes", "owner");

    private static List<String> changedFields(List<String> before, List<String> after) {
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < AUDITED_FIELDS.size(); i++) {
            if (!java.util.Objects.equals(before.get(i), after.get(i))) {
                changed.add(AUDITED_FIELDS.get(i));
            }
        }
        return changed;
    }

    private Customer findCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        return customer;
    }

    private CustomerContact findContact(Customer customer, Long contactId) {
        return customer.getContacts().stream()
                .filter(c -> c.getId().equals(contactId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Contact not found"));
    }

    /** sales_executive may only view accounts assigned to them ("own" data scope). */
    private void assertViewAccess(User currentUser, Customer customer) {
        dataScopeResolver.assertCanAccess(currentUser,
                customer.getOwner() != null ? customer.getOwner().getId() : null, "customer");
    }

    /** sales_executive may only edit accounts assigned to them; everyone else in the tenant may edit any. */
    private void assertEditAccess(User currentUser, Customer customer) {
        assertViewAccess(currentUser, customer);
    }

    /** Dashboard counts, aggregated in the database within the caller's data scope. */
    @Transactional(readOnly = true)
    public CustomerSummaryDto getSummary(User currentUser) {
        return customerSummaryQuery.summarize(requireTenantId(currentUser), dataScopeResolver.resolve(currentUser),
                reportingPeriods.startOfQuarter());
    }

    /** Records the user may see, as decided by DataScopeResolver (tenant-wide, team or own). */
    private List<Customer> findInScope(User currentUser) {
        Long tenantId = requireTenantId(currentUser);
        DataScope scope = dataScopeResolver.resolve(currentUser);
        return scope.isTenantWide()
                ? customerRepository.findByTenantId(tenantId)
                : customerRepository.findByTenantIdAndOwnerIdIn(tenantId, scope.ownerIds());
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Customer accounts are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
