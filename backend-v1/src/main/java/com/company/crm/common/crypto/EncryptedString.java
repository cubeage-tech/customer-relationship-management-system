package com.company.crm.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** NFR-7.2b: put {@code @Convert(converter = EncryptedString.class)} on a column to store it encrypted. */
@Converter
public class EncryptedString implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String value) {
        return value == null ? null : DataEncryption.get().encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : DataEncryption.get().decrypt(stored);
    }
}
