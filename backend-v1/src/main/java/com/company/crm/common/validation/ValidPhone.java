package com.company.crm.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * VAL-2: an optional phone number that must be valid for the configured country (blank is
 * allowed; add @NotBlank when the number is required). See PhoneNumbers.
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "must be a valid phone number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
