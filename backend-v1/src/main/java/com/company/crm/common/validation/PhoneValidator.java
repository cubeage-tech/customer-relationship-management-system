package com.company.crm.common.validation;

import com.company.crm.communication.service.PhoneNumbers;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** VAL-2: checks @ValidPhone fields against the configured country (Spring injects PhoneNumbers). */
@RequiredArgsConstructor
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    private final PhoneNumbers phoneNumbers;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank() || phoneNumbers.isValid(value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be a valid phone number for country code "
                + phoneNumbers.defaultCountryCode() + ", or start with + and the country code")
                .addConstraintViolation();
        return false;
    }
}
