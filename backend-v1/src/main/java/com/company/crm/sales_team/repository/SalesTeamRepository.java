package com.company.crm.sales_team.repository;

import com.company.crm.sales_team.entity.SalesTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesTeamRepository extends JpaRepository<SalesTeam, Long> {

    @EntityGraph(attributePaths = "manager")
    List<SalesTeam> findByTenantIdOrderByNameAsc(Long tenantId);

    @EntityGraph(attributePaths = "manager")
    Optional<SalesTeam> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);

    /** Ids of the teams this user manages within the tenant. */
    @Query("select t.id from SalesTeam t where t.tenant.id = :tenantId and t.manager.id = :managerId")
    List<Long> findIdsManagedBy(@Param("tenantId") Long tenantId, @Param("managerId") Long managerId);
}
