package com.company.crm.industry.repository;

import com.company.crm.industry.entity.Industry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndustryRepository extends JpaRepository<Industry, Industry.Key> {

    List<Industry> findByTenantIdOrderBySortOrderAscLabelAsc(Long tenantId);

    boolean existsByTenantId(Long tenantId);

    boolean existsByTenantIdAndLabelIgnoreCase(Long tenantId, String label);

    long countByTenantIdAndActiveTrue(Long tenantId);

    /** Idempotent: concurrent first uses for the same tenant cannot clash. */
    @Modifying
    @Query(value = "INSERT INTO industries (tenant_id, code, label, sort_order) VALUES (:tenantId, :code, :label, :sortOrder)"
            + " ON CONFLICT DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("tenantId") Long tenantId, @Param("code") String code,
                        @Param("label") String label, @Param("sortOrder") int sortOrder);
}
