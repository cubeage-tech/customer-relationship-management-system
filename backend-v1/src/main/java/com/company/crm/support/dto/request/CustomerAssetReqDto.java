package com.company.crm.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** FR-6.1: add or edit a customer's asset. */
@Getter
@Setter
public class CustomerAssetReqDto {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 100)
    private String model;

    @Size(max = 100)
    private String serialNumber;

    private LocalDate installedOn;

    private String notes;
}
