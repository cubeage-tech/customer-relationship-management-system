package com.company.crm.customer.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, Long> {

    List<CustomerDocument> findByTenantIdAndCustomerIdOrderByUploadedAtDescIdDesc(
            Long tenantId, Long customerId);

    Optional<CustomerDocument> findByIdAndTenantIdAndCustomerId(
            Long id, Long tenantId, Long customerId);
}
