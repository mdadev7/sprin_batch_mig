package com.example.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CryptoConfigTest {

    @Test
    void defaults_are_valid() {
        CryptoConfig c = CryptoConfig.defaults();
        assertEquals("v1", c.getFormatVersion());
        assertEquals("v1:", c.formatPrefix());
        assertEquals("APP_CRYPTO_KEY", c.getKeyEnvironmentVariable());
    }

    @Test
    void rejects_unknown_transformation() {
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/CBC/PKCS5Padding", "APP_CRYPTO_KEY", 12, 128, "v1"));
    }

    @Test
    void rejects_bad_iv_or_tag() {
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/GCM/NoPadding", "APP_CRYPTO_KEY", 16, 128, "v1"));
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/GCM/NoPadding", "APP_CRYPTO_KEY", 12, 96, "v1"));
    }

    @Test
    void rejects_bad_env_var_name() {
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/GCM/NoPadding", "1BAD", 12, 128, "v1"));
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/GCM/NoPadding", null, 12, 128, "v1"));
    }

    @Test
    void rejects_bad_format_version() {
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/GCM/NoPadding", "APP_CRYPTO_KEY", 12, 128, "v1:bad"));
        assertThrows(IllegalArgumentException.class,
            () -> new CryptoConfig("AES/GCM/NoPadding", "APP_CRYPTO_KEY", 12, 128, null));
    }

    @Test
    void equality_by_value() {
        CryptoConfig a = CryptoConfig.defaults();
        CryptoConfig b = CryptoConfig.defaults();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}