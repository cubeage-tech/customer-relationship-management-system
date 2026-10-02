package com.company.crm.customer.repository;

import com.company.crm.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);

    List<Customer> findByTenantId(Long tenantId);

    List<Customer> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    List<Customer> findByTenantIdAndOwnerIdIn(Long tenantId, Collection<Long> ownerIds);

    long countByTenantId(Long tenantId);
}
