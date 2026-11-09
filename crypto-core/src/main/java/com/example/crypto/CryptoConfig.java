package com.example.crypto;

import java.util.Objects;

/**
 * Paramètres du chiffrement. Ne contient AUCUN secret — uniquement
 * la configuration publique (algorithme, tailles, préfixe de version,
 * nom de la variable d'environnement portant la clé).
 *
 * Immutable, thread-safe.
 */
public final class CryptoConfig {

    public static final String DEFAULT_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int DEFAULT_IV_LENGTH = 12;
    public static final int DEFAULT_TAG_LENGTH = 128;
    public static final String DEFAULT_FORMAT_VERSION = "v1";
    public static final String DEFAULT_KEY_ENV_VAR = "APP_CRYPTO_KEY";

    private final String transformation;
    private final String keyEnvironmentVariable;
    private final int ivLength;
    private final int tagLength;
    private final String formatVersion;

    public CryptoConfig(
            String transformation,
            String keyEnvironmentVariable,
            int ivLength,
            int tagLength,
            String formatVersion) {

        this.transformation = transformation;
        this.keyEnvironmentVariable = keyEnvironmentVariable;
        this.ivLength = ivLength;
        this.tagLength = tagLength;
        this.formatVersion = formatVersion;

        validate();
    }

    /** Configuration de référence (spec §3.2), version v1, variable APP_CRYPTO_KEY. */
    public static CryptoConfig defaults() {
        return new CryptoConfig(
            DEFAULT_TRANSFORMATION,
            DEFAULT_KEY_ENV_VAR,
            DEFAULT_IV_LENGTH,
            DEFAULT_TAG_LENGTH,
            DEFAULT_FORMAT_VERSION);
    }

    /** Même algo/tailles, autre variable d'environnement (ex. APP_CRYPTO_KEY_V2). */
    public static CryptoConfig withEnvVar(String envVar) {
        return new CryptoConfig(
            DEFAULT_TRANSFORMATION, envVar,
            DEFAULT_IV_LENGTH, DEFAULT_TAG_LENGTH, DEFAULT_FORMAT_VERSION);
    }

    /** Même algo/tailles, autre variable d'env ET autre version de format. */
    public static CryptoConfig ofVersion(String formatVersion, String envVar) {
        return new CryptoConfig(
            DEFAULT_TRANSFORMATION, envVar,
            DEFAULT_IV_LENGTH, DEFAULT_TAG_LENGTH, formatVersion);
    }

    private void validate() {
        if (!DEFAULT_TRANSFORMATION.equals(transformation)) {
            throw new IllegalArgumentException(
                "Transformation non supportée : " + transformation);
        }
        if (ivLength != DEFAULT_IV_LENGTH || tagLength != DEFAULT_TAG_LENGTH) {
            throw new IllegalArgumentException(
                "Paramètres AES-GCM invalides (iv=" + ivLength + ", tag=" + tagLength + ")");
        }
        if (keyEnvironmentVariable == null
                || !keyEnvironmentVariable.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                "Nom de variable d'environnement invalide : " + keyEnvironmentVariable);
        }
        if (formatVersion == null
                || !formatVersion.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException(
                "Version de format invalide : " + formatVersion);
        }
    }

    /** Préfixe réellement écrit dans le ciphertext, ex. "v1:". */
    public String formatPrefix() {
        return formatVersion + ":";
    }

    public String getTransformation() { return transformation; }
    public String getKeyEnvironmentVariable() { return keyEnvironmentVariable; }
    public int getIvLength() { return ivLength; }
    public int getTagLength() { return tagLength; }
    public String getFormatVersion() { return formatVersion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CryptoConfig)) return false;
        CryptoConfig that = (CryptoConfig) o;
        return ivLength == that.ivLength
            && tagLength == that.tagLength
            && transformation.equals(that.transformation)
            && keyEnvironmentVariable.equals(that.keyEnvironmentVariable)
            && formatVersion.equals(that.formatVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformation, keyEnvironmentVariable,
                            ivLength, tagLength, formatVersion);
    }

    @Override
    public String toString() {
        return "CryptoConfig{" +
            "transformation='" + transformation + '\'' +
            ", keyEnvironmentVariable='" + keyEnvironmentVariable + '\'' +
            ", ivLength=" + ivLength +
            ", tagLength=" + tagLength +
            ", formatVersion='" + formatVersion + '\'' +
            '}';
    }
}