package com.example.crypto;

/**
 * Exception non vérifiée, levée pour toute erreur cryptographique
 * (chiffrement, déchiffrement, format, clé).
 *
 * Étend RuntimeException directement (PAS IllegalArgumentException) :
 * sinon les throw new CryptoException(...) internes seraient attrapés
 * et re-emballés par les catch (IllegalArgumentException).
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}