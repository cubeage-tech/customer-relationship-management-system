package com.company.crm.support.dto.response;

import java.time.LocalDateTime;

/** FR-6.5: what the customer's feedback page shows; score is non-null once feedback was given. */
public record PublicFeedbackDto(String companyName, String subject, String technicianName, LocalDateTime resolvedAt,
                                Integer score, String comment) {}
