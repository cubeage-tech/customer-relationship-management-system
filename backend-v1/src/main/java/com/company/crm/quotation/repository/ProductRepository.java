package com.company.crm.quotation.repository;

import com.company.crm.quotation.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    List<Product> findByTenantId(Long tenantId);
}
