package com.company.crm.user.repository;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByTenantId(Long tenantId);
    long countByTenantId(Long tenantId);
    long countByTenantIdAndStatusNot(Long tenantId, AccountStatus status);

    /** Ids of users in the given teams — the owners a team-scoped sales manager may see. */
    @Query("select u.id from User u where u.tenant.id = :tenantId and u.teamId in :teamIds")
    List<Long> findIdsByTenantIdAndTeamIdIn(@Param("tenantId") Long tenantId, @Param("teamIds") Collection<Long> teamIds);

    List<User> findByTenantIdAndTeamId(Long tenantId, Long teamId);

    /** Every team member in the tenant, in one query (avoids one query per team when listing). */
    List<User> findByTenantIdAndTeamIdIsNotNull(Long tenantId);

    List<User> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    /** Un-assigns every member of a team (used when it is deleted or its roster replaced). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.teamId = null where u.tenant.id = :tenantId and u.teamId = :teamId")
    int clearTeam(@Param("tenantId") Long tenantId, @Param("teamId") Long teamId);
}
