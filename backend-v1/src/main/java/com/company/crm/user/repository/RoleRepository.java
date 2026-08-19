package com.company.crm.user.repository;

import com.company.crm.common.enums.RoleType;
import com.company.crm.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleType name);
}
