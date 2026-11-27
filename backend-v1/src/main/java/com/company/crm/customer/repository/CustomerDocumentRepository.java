package com.company.crm.customer.repository;

import com.company.crm.customer.entity.CustomerDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, Long> {

    /** FR-1.5: a customer's documents, newest first. */
    @EntityGraph(attributePaths = {"uploadedBy"})
    List<CustomerDocument> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(Long tenantId, Long customerId);

    Optional<CustomerDocument> findByIdAndTenantIdAndCustomerId(Long id, Long tenantId, Long customerId);
}
