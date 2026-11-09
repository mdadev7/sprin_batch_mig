package com.example.crypto;

import javax.crypto.SecretKey;

/**
 * Fournit la clé AES utilisée par CryptoService.
 * Contrat : getKey() ne retourne jamais null et la clé fait 32 octets.
 */
public interface KeyProvider {
    SecretKey getKey();
}