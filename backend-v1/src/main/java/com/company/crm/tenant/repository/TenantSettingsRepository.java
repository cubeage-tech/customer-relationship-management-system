package com.company.crm.tenant.repository;

import com.company.crm.tenant.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
}
