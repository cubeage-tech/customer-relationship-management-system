package com.company.crm.quotation.repository;

import com.company.crm.quotation.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByTenantId(Long tenantId);
}
