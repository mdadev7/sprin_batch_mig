package com.example.crypto; 

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * Test utilitaire — NE PAS garder en CI.
 * Génère le contenu de fixture-v1.txt à copier manuellement.
 * À lancer une seule fois : mvn -Dtest=GenerateFixtureTest test
 */
class GenerateFixtureTest {

    @Test
    void generate() {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = 0x01;
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        KeyProvider provider = () -> key;
        CryptoService svc = new CryptoService(provider, CryptoConfig.defaults());

        String plain = "3200.50";
        String cipher = svc.encrypt(plain);

        System.out.println("=== Copier dans src/test/resources/fixture-v1.txt ===");
        System.out.println(plain);
        System.out.println(cipher);
        System.out.println("====================================================");
    }
}