package com.example.legacy.converter;

import com.example.crypto.CryptoHolder;
import com.example.crypto.CryptoService;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import java.math.BigDecimal;

@Converter
public class FloatCryptedConverter
        implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal salaire) {

        if (salaire == null) {
            return null;
        }

        CryptoService crypto = CryptoHolder.get();

        return crypto.encrypt(salaire.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String encrypted) {

        if (encrypted == null) {
            return null;
        }

        CryptoService crypto = CryptoHolder.get();

        String decrypted = crypto.decrypt(encrypted);

        return new BigDecimal(decrypted);
    }
}