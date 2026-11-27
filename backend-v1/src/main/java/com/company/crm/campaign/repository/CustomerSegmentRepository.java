package com.company.crm.campaign.repository;

import com.company.crm.campaign.entity.CustomerSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, Long> {

    List<CustomerSegment> findByTenantIdOrderByNameAsc(Long tenantId);

    Optional<CustomerSegment> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);
}
