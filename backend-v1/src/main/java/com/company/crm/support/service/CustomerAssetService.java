package com.company.crm.support.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.support.dto.request.CustomerAssetReqDto;
import com.company.crm.support.dto.response.CustomerAssetResDto;
import com.company.crm.support.entity.CustomerAsset;
import com.company.crm.support.repository.CustomerAssetRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-6.1: the equipment/products a customer owns, so service tickets can say which one is affected. */
@Service
@RequiredArgsConstructor
public class CustomerAssetService {

    private final CustomerAssetRepository assetRepository;
    private final CustomerRepository customerRepository;
    private final DataScopeResolver dataScopeResolver;

    @Transactional(readOnly = true)
    public List<CustomerAssetResDto> list(User currentUser, Long customerId) {
        Customer customer = findCustomer(currentUser, customerId);
        return assetRepository.findByTenantIdAndCustomerIdOrderByNameAsc(customer.getTenant().getId(), customerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public CustomerAssetResDto create(User currentUser, Long customerId, CustomerAssetReqDto dto) {
        Customer customer = findCustomer(currentUser, customerId);
        CustomerAsset asset = new CustomerAsset();
        asset.setTenant(customer.getTenant());
        asset.setCustomer(customer);
        applyFields(asset, dto);
        return toDto(assetRepository.save(asset));
    }

    @Transactional
    public CustomerAssetResDto update(User currentUser, Long customerId, Long assetId, CustomerAssetReqDto dto) {
        CustomerAsset asset = findAsset(currentUser, customerId, assetId);
        applyFields(asset, dto);
        return toDto(assetRepository.save(asset));
    }

    /** Tickets that referenced the asset keep their history; the link is cleared (ON DELETE SET NULL). */
    @Transactional
    public void delete(User currentUser, Long customerId, Long assetId) {
        assetRepository.delete(findAsset(currentUser, customerId, assetId));
    }

    /** For ticket create/edit: an asset of this tenant's customer, or 400. */
    @Transactional(readOnly = true)
    public CustomerAsset requireAssetOf(Customer customer, Long assetId) {
        return assetRepository.findByIdAndTenantId(assetId, customer.getTenant().getId())
                .filter(a -> a.getCustomer().getId().equals(customer.getId()))
                .orElseThrow(() -> ApiException.badRequest("The asset must belong to this customer"));
    }

    // ==================== Helpers ====================

    private void applyFields(CustomerAsset asset, CustomerAssetReqDto dto) {
        asset.setName(dto.getName().trim());
        asset.setModel(blankToNull(dto.getModel()));
        asset.setSerialNumber(blankToNull(dto.getSerialNumber()));
        asset.setInstalledOn(dto.getInstalledOn());
        asset.setNotes(blankToNull(dto.getNotes()));
    }

    private CustomerAsset findAsset(User currentUser, Long customerId, Long assetId) {
        Customer customer = findCustomer(currentUser, customerId);
        return assetRepository.findByIdAndTenantId(assetId, customer.getTenant().getId())
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Asset not found"));
    }

    private Customer findCustomer(User currentUser, Long customerId) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Assets are scoped to a tenant");
        }
        Customer customer = customerRepository.findByIdAndTenantId(customerId, currentUser.getTenant().getId())
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        dataScopeResolver.assertCanAccess(currentUser, customer.getOwner() != null ? customer.getOwner().getId() : null, "customer");
        return customer;
    }

    private CustomerAssetResDto toDto(CustomerAsset a) {
        return new CustomerAssetResDto(a.getId(), a.getCustomer().getId(), a.getName(), a.getModel(), a.getSerialNumber(),
                a.getInstalledOn(), a.getNotes());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
