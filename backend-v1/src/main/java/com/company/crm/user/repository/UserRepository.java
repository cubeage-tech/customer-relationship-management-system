package com.company.crm.user.repository;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
