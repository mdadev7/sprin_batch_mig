package com.example.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RotatingCryptoServiceTest {

    private static KeyProvider key(byte fill) {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) b[i] = fill;
        SecretKey k = new SecretKeySpec(b, "AES");
        return () -> k;
    }

    private static RotatingCryptoService rotatingV1V2() {
        Map<String, CryptoService> services = new LinkedHashMap<>();
        services.put("v1",
            new CryptoService(key((byte) 0x01), CryptoConfig.defaults()));
        services.put("v2",
            new CryptoService(key((byte) 0x02),
                CryptoConfig.ofVersion("v2", "APP_CRYPTO_KEY_V2")));
        return new RotatingCryptoService(services, "v2");
    }

    @Test
    void encrypt_uses_current_version() {
        RotatingCryptoService r = rotatingV1V2();
        String cipher = r.encrypt("1000");
        assertTrue(cipher.startsWith("v2:"));
        assertEquals("v2", r.currentVersion());
    }

    @Test
    void decrypt_routes_by_prefix() {
        RotatingCryptoService r = rotatingV1V2();

        // Produire un ciphertext v1 via le service v1 sous-jacent
        String v1Cipher = r.serviceFor("v1").encrypt("42");
        String v2Cipher = r.encrypt("42");

        assertEquals("42", r.decrypt(v1Cipher));
        assertEquals("42", r.decrypt(v2Cipher));
    }

    @Test
    void decrypt_rejects_unknown_prefix() {
        RotatingCryptoService r = rotatingV1V2();
        assertThrows(CryptoException.class, () -> r.decrypt("v3:abc"));
        assertThrows(CryptoException.class, () -> r.decrypt("plain"));
    }

    @Test
    void current_version_must_exist() {
        Map<String, CryptoService> services = new LinkedHashMap<>();
        services.put("v1",
            new CryptoService(key((byte) 0x01), CryptoConfig.defaults()));
        assertThrows(IllegalArgumentException.class,
            () -> new RotatingCryptoService(services, "v2"));
    }

    @Test
    void serviceFor_unknown_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> rotatingV1V2().serviceFor("v9"));
    }
}