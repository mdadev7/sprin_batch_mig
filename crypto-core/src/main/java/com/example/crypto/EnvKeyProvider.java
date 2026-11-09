package com.example.crypto;

import java.util.Base64;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lit la clé AES-256 (32 octets, Base64) depuis une variable
 * d'environnement dont le nom est fourni par CryptoConfig.
 */
public final class EnvKeyProvider implements KeyProvider {

    private final SecretKey key;

    public EnvKeyProvider(CryptoConfig config) {
        Objects.requireNonNull(config, "CryptoConfig requis");

        String variable = config.getKeyEnvironmentVariable();
        String encodedKey = System.getenv(variable);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalStateException("Variable de clé absente : " + variable);
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());

            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                    "La clé AES doit faire 32 octets (reçu : " + decoded.length + ")");
            }

            this.key = new SecretKeySpec(decoded, "AES");

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Clé AES invalide (Base64/longueur) dans la variable " + variable, e);
        }
    }

    /** Constructeur de commodité : nom de variable passé directement. */
    public EnvKeyProvider(String envVariableName) {
        this(new CryptoConfig(
            CryptoConfig.DEFAULT_TRANSFORMATION,
            envVariableName,
            CryptoConfig.DEFAULT_IV_LENGTH,
            CryptoConfig.DEFAULT_TAG_LENGTH,
            CryptoConfig.DEFAULT_FORMAT_VERSION));
    }

    @Override
    public SecretKey getKey() {
        return key;
    }
}