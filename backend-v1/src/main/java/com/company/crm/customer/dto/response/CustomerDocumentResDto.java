package com.company.crm.customer.dto.response;

import java.time.LocalDateTime;

/** FR-1.5: one document attached to a customer. */
public record CustomerDocumentResDto(
        Long id,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploadedByName,
        LocalDateTime uploadedAt
) {}
