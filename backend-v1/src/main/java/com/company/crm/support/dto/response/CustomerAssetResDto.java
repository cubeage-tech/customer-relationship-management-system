package com.company.crm.support.dto.response;

import java.time.LocalDate;

/** FR-6.1: one of a customer's assets. */
public record CustomerAssetResDto(Long id, Long customerId, String name, String model, String serialNumber,
                                  LocalDate installedOn, String notes) {}
