package com.company.crm.customer.service;

import com.company.crm.common.enums.CustomerStatus;
import com.company.crm.common.enums.IndustryType;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.dto.request.CustomerContactReqDto;
import com.company.crm.customer.dto.request.CustomerReqDto;
import com.company.crm.customer.dto.response.CustomerContactResDto;
import com.company.crm.customer.dto.response.CustomerResDto;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.customer.mapper.CustomerMapper;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerMapper customerMapper;

    // open-in-view is disabled (see application.properties) — the mapper walks lazy
    // associations (contacts, owner, tenant), so the session must stay open through mapping.
    @Transactional(readOnly = true)
    public List<CustomerResDto> listCustomers(User currentUser, String industry, String status, String search) {
        List<Customer> customers = currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                ? customerRepository.findByTenantIdAndOwnerId(requireTenantId(currentUser), currentUser.getId())
                : customerRepository.findByTenantId(requireTenantId(currentUser));

        return customers.stream()
                .filter(c -> industry == null || industry.isBlank() || c.getIndustry().getDbValue().equals(industry))
                .filter(c -> status == null || status.isBlank() || c.getStatus().getDbValue().equals(status))
                .filter(c -> search == null || search.isBlank()
                        || c.getCompanyName().toLowerCase().contains(search.trim().toLowerCase()))
                .map(customerMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResDto getCustomer(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertViewAccess(currentUser, customer);
        return customerMapper.toDto(customer);
    }

    @Transactional
    public CustomerResDto createCustomer(User currentUser, CustomerReqDto dto) {
        Customer customer = new Customer();
        customer.setTenant(currentUser.getTenant());
        customer.setCreatedBy(currentUser);
        applyFields(currentUser, customer, dto);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto updateCustomer(User currentUser, Long customerId, CustomerReqDto dto) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);
        applyFields(currentUser, customer, dto);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto archiveCustomer(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        customer.setStatus(CustomerStatus.ARCHIVED);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto restoreCustomer(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        customer.setStatus(CustomerStatus.ACTIVE);
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

        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto updateContact(User currentUser, Long customerId, Long contactId, CustomerContactReqDto dto) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);

        CustomerContact contact = findContact(customer, contactId);
        applyContactFields(contact, dto);

        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResDto deleteContact(User currentUser, Long customerId, Long contactId) {
        Customer customer = findCustomer(currentUser, customerId);
        assertEditAccess(currentUser, customer);

        CustomerContact contact = findContact(customer, contactId);
        customer.getContacts().remove(contact);

        return customerMapper.toDto(customerRepository.save(customer));
    }

    // ==================== Helpers ====================

    private void applyFields(User currentUser, Customer customer, CustomerReqDto dto) {
        customer.setCompanyName(dto.getCompanyName());
        customer.setIndustry(parseIndustry(dto.getIndustry()));
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
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.badRequest("Owner not found"));
        if (owner.getTenant() == null || !owner.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.badRequest("Owner must belong to your tenant");
        }
        return owner;
    }

    private IndustryType parseIndustry(String rawIndustry) {
        try {
            return IndustryType.fromDbValue(rawIndustry);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown industry: " + rawIndustry);
        }
    }

    private Customer findCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        if (!customer.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Customer not found");
        }
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
        if (currentUser.getRole().getName() == RoleType.SALES_EXECUTIVE
                && (customer.getOwner() == null || !customer.getOwner().getId().equals(currentUser.getId()))) {
            throw ApiException.forbidden("You do not have access to this customer");
        }
    }

    /** sales_executive may only edit accounts assigned to them; everyone else in the tenant may edit any. */
    private void assertEditAccess(User currentUser, Customer customer) {
        assertViewAccess(currentUser, customer);
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Customer accounts are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
