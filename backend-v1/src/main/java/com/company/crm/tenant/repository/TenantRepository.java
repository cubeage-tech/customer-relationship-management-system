package com.company.crm.tenant.repository;

import com.company.crm.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /** FR-6.1: the tenant behind a public support form link. */
    Optional<Tenant> findBySupportFormToken(String supportFormToken);
}
