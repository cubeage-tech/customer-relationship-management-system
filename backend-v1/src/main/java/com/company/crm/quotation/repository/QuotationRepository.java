package com.company.crm.quotation.repository;

import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.quotation.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    /** Tenant-scoped lookup: a record from another tenant is indistinguishable from a missing one. */
    Optional<Quotation> findByIdAndTenantId(Long id, Long tenantId);

    List<Quotation> findByTenantId(Long tenantId);

    List<Quotation> findByTenantIdAndOwnerId(Long tenantId, Long ownerId);

    List<Quotation> findByTenantIdAndOwnerIdIn(Long tenantId, Collection<Long> ownerIds);

    /** FR-1.3: a customer's quotations in one status (APPROVED = purchases). */
    List<Quotation> findByTenantIdAndCustomerIdAndStatus(Long tenantId, Long customerId, QuotationStatus status);

    long countByTenantId(Long tenantId);

    /** FR-4.5: the quotation behind a customer view link. */
    Optional<Quotation> findByViewToken(String viewToken);

    /** FR-4.5: moves sent-but-unanswered quotations past their valid-until date to EXPIRED. */
    @Modifying
    @Query("update Quotation q set q.status = :expired, q.updatedAt = :now"
            + " where q.status in :open and q.validUntil < :today")
    int expireOverdue(@Param("expired") QuotationStatus expired,
                      @Param("open") Collection<QuotationStatus> open,
                      @Param("today") LocalDate today,
                      @Param("now") LocalDateTime now);
}
