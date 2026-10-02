package com.company.crm.quotation.repository;

import com.company.crm.quotation.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Quotation> findByIdAndTenantId(Long id, Long tenantId);

    List<Quotation> findByTenantId(Long tenantId);

    List<Quotation> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    List<Quotation> findByTenantIdAndOwnerIdIn(Long tenantId, Collection<Long> ownerIds);

    long countByTenantId(Long tenantId);
}
