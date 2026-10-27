package com.company.crm.customfield.repository;

import com.company.crm.customfield.entity.CustomFieldValues;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomFieldValuesRepository extends JpaRepository<CustomFieldValues, Long> {

    Optional<CustomFieldValues> findByTenantIdAndEntityTypeAndRecordId(Long tenantId, String entityType, Long recordId);
}
