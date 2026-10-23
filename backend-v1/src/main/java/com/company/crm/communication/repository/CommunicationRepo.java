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

    /** The same timeline for a lead that has not been converted yet. */
    @EntityGraph(attributePaths = {"createdBy"})
    List<Communication> findByTenantIdAndLeadIdOrderByOccurredAtDesc(Long tenantId, Long leadId);

    /** Delivery callbacks and duplicate checks. */
    Optional<Communication> findByProviderMessageId(String providerMessageId);

    /** Email replies: In-Reply-To / References point at the Message-ID we sent. */
    List<Communication> findByProviderMessageIdIn(Collection<String> providerMessageIds);

    /** The latest message we sent to this address/number — the most reliable owner of a reply. */
    Optional<Communication> findFirstByChannelAndDirectionAndCounterpartOrderByOccurredAtDesc(
            CommunicationChannel channel, CommunicationDirection direction, String counterpart);
}
