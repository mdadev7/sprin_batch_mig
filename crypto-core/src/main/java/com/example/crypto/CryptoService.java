package com.example.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Service de chiffrement AES-256-GCM pour UNE version de format donnée.
 * Format : {formatPrefix}Base64(IV || ciphertext || tag).
 *
 * Tous les paramètres (algo, tailles, préfixe) viennent de CryptoConfig.
 * Thread-safe : aucun état mutable partagé, IV aléatoire par opération.
 *
 * Pour gérer plusieurs versions simultanément (rotation v1:/v2:),
 * utiliser RotatingCryptoService.
 */
public final class CryptoService {

    private final KeyProvider keyProvider;
    private final CryptoConfig config;
    private final SecureRandom random;

    public CryptoService(KeyProvider keyProvider, CryptoConfig config) {
        if (keyProvider == null) throw new IllegalArgumentException("KeyProvider requis");
        if (config == null) throw new IllegalArgumentException("CryptoConfig requis");
        this.keyProvider = keyProvider;
        this.config = config;
        // SecureRandom est thread-safe côté JDK ; une instance par CryptoService
        // est acceptable car CryptoService est un singleton applicatif.
        this.random = new SecureRandom();
    }

    public String encrypt(String value) {
        if (value == null) return null;

        try {
            byte[] iv = new byte[config.getIvLength()];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(config.getTransformation());
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getKey(),
                        new GCMParameterSpec(config.getTagLength(), iv));

            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);

            return config.formatPrefix()
                 + Base64.getEncoder().encodeToString(output);

        } catch (GeneralSecurityException e) {
            throw new CryptoException("Erreur de chiffrement", e);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;

        String prefix = config.formatPrefix();
        if (!encryptedValue.startsWith(prefix)) {
            throw new CryptoException(
                "Format de chiffrement inconnu (préfixe '" + prefix + "' attendu)");
        }

        byte[] data;
        try {
            data = Base64.getDecoder().decode(
                encryptedValue.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw new CryptoException("Ciphertext non-Base64", e);
        }

        int ivLength = config.getIvLength();
        int tagLengthBytes = config.getTagLength() / 8; // tagLength en bits (128 → 16)
        if (data.length < ivLength + tagLengthBytes) {
            throw new CryptoException("Données chiffrées invalides (trop courtes)");
        }

        try {
            byte[] iv = new byte[ivLength];
            byte[] encrypted = new byte[data.length - ivLength];
            System.arraycopy(data, 0, iv, 0, ivLength);
            System.arraycopy(data, ivLength, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(config.getTransformation());
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getKey(),
                        new GCMParameterSpec(config.getTagLength(), iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                "Déchiffrement impossible ou données altérées", e);
        }
    }
}