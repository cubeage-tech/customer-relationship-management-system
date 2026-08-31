package com.company.crm.customer.repository;

import com.company.crm.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenantId(Long tenantId);

    List<Customer> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);
}
