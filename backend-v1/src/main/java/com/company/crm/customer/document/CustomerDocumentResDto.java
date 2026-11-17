package com.company.crm.customer.document;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CustomerDocumentResDto {
    private Long id;
    private String originalFilename;
    private String contentType;
    private long fileSize;
    private LocalDateTime uploadedAt;
    private Long uploadedById;
    private String uploadedByName;
}
