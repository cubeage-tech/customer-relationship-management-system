package com.company.crm.communication.repository;

import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.communication.entity.Communication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunicationRepo extends JpaRepository<Communication, Long> {

    /** FR-5.6: a customer's timeline, newest first. */
    @EntityGraph(attributePaths = {"createdBy"})
    List<Communication> findByTenantIdAndCustomerIdOrderByOccurredAtDesc(Long tenantId, Long customerId);

    /** UI-1 unified inbox: the tenant's latest messages and calls. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"customer", "customer.owner", "lead", "lead.owner", "createdBy"})
    List<Communication> findTop500ByTenantIdOrderByOccurredAtDesc(Long tenantId);

    /** The same timeline for a lead that has not been converted yet. */
    @EntityGraph(attributePaths = {"createdBy"})
    List<Communication> findByTenantIdAndLeadIdOrderByOccurredAtDesc(Long tenantId, Long leadId);

    /** Delivery callbacks and duplicate checks. */
    Optional<Communication> findByProviderMessageId(String providerMessageId);

    /** NFR-7.4a: SMS/WhatsApp messages waiting for an automatic resend. */
    @org.springframework.data.jpa.repository.Query("select c.id from Communication c where c.nextRetryAt is not null and c.nextRetryAt <= :now order by c.nextRetryAt")
    List<Long> findRetryDueIds(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /** Leases one due resend (returns 0 if another instance took it). */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Communication c set c.nextRetryAt = :lease where c.id = :id and c.nextRetryAt = :due")
    int leaseRetry(@org.springframework.data.repository.query.Param("id") Long id,
                   @org.springframework.data.repository.query.Param("due") java.time.LocalDateTime due,
                   @org.springframework.data.repository.query.Param("lease") java.time.LocalDateTime lease);

    /** Email replies: In-Reply-To / References point at the Message-ID we sent. */
    List<Communication> findByProviderMessageIdIn(Collection<String> providerMessageIds);

    /** The latest message we sent to this address/number — the most reliable owner of a reply. */
    /** NFR-7.2b: counterparts are encrypted, so this looks them up by their blind index. */
    Optional<Communication> findFirstByChannelAndDirectionAndCounterpartHashOrderByOccurredAtDesc(
            CommunicationChannel channel, CommunicationDirection direction, String counterpartHash);
}
