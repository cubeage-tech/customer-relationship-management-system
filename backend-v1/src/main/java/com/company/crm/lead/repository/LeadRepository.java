package com.company.crm.lead.repository;

import com.company.crm.lead.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Lead> findByIdAndTenantId(Long id, Long tenantId);

    List<Lead> findByTenantId(Long tenantId);

    List<Lead> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    List<Lead> findByTenantIdAndOwnerIdIn(Long tenantId, Collection<Long> ownerIds);

    /** FR-2.3: leads owned by someone in scope, or assigned to one of the given teams (owner may be empty). */
    @Query("select l from Lead l left join l.owner o left join l.team t"
            + " where l.tenant.id = :tenantId and (o.id in :ownerIds or t.id in :teamIds)")
    List<Lead> findInScopeOrTeams(@Param("tenantId") Long tenantId,
                                  @Param("ownerIds") Collection<Long> ownerIds,
                                  @Param("teamIds") Collection<Long> teamIds);

    List<Lead> findByTenantIdAndCampaignId(Long tenantId, Long campaignId);

    List<Lead> findByTenantIdAndCampaignIdIsNotNull(Long tenantId);

    long countByTenantId(Long tenantId);
}
