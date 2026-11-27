package com.company.crm.customfield.repository;

import com.company.crm.customfield.entity.CustomFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, Long> {

    List<CustomFieldDefinition> findByTenantIdOrderByEntityTypeAscPositionAscIdAsc(Long tenantId);

    List<CustomFieldDefinition> findByTenantIdAndEntityTypeOrderByPositionAscIdAsc(Long tenantId, String entityType);

    Optional<CustomFieldDefinition> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndEntityTypeAndFieldKey(Long tenantId, String entityType, String fieldKey);
}
