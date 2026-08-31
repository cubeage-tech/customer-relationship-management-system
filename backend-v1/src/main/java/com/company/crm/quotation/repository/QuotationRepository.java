package com.company.crm.quotation.repository;

import com.company.crm.quotation.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findByTenantId(Long tenantId);

    List<Quotation> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    long countByTenantId(Long tenantId);
}
