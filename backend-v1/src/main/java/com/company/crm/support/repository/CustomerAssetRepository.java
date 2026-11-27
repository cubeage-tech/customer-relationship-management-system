package com.company.crm.support.repository;

import com.company.crm.support.entity.CustomerAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAssetRepository extends JpaRepository<CustomerAsset, Long> {

    List<CustomerAsset> findByTenantIdAndCustomerIdOrderByNameAsc(Long tenantId, Long customerId);

    Optional<CustomerAsset> findByIdAndTenantId(Long id, Long tenantId);
}
