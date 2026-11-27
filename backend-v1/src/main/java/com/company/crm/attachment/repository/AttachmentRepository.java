package com.company.crm.attachment.repository;

import com.company.crm.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<Attachment> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(Long tenantId, String entityType, Long entityId);

    Optional<Attachment> findByIdAndTenantIdAndEntityTypeAndEntityId(Long id, Long tenantId, String entityType, Long entityId);
}
