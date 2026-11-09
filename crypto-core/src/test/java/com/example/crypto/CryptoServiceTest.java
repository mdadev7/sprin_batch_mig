package com.example.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class CryptoServiceTest {

    private static final byte[] KEY_BYTES = new byte[32];
    static { for (int i = 0; i < KEY_BYTES.length; i++) KEY_BYTES[i] = 0x01; }

    private static KeyProvider fixedKey() {
        SecretKey k = new SecretKeySpec(KEY_BYTES, "AES");
        return () -> k;
    }

    private static CryptoService serviceV1() {
        return new CryptoService(fixedKey(), CryptoConfig.defaults());
    }

    @Test
    void roundTrip() {
        CryptoService c = serviceV1();
        String cipher = c.encrypt("3200.50");
        assertTrue(cipher.startsWith("v1:"));
        assertEquals("3200.50", c.decrypt(cipher));
    }

    @Test
    void nonDeterministic() {
        CryptoService c = serviceV1();
        String a = c.encrypt("1000");
        String b = c.encrypt("1000");
        assertNotEquals(a, b);
        assertEquals("1000", c.decrypt(a));
        assertEquals("1000", c.decrypt(b));
    }

    @Test
    void null_passthrough() {
        CryptoService c = serviceV1();
        assertNull(c.encrypt(null));
        assertNull(c.decrypt(null));
    }

    @Test
    void decrypt_rejects_unknown_prefix() {
        CryptoService c = serviceV1();
        assertThrows(CryptoException.class, () -> c.decrypt("plain"));
        assertThrows(CryptoException.class, () -> c.decrypt("v2:whatever"));
    }

    @Test
    void decrypt_rejects_non_base64() {
        CryptoService c = serviceV1();
        assertThrows(CryptoException.class, () -> c.decrypt("v1:not@base64"));
    }

    @Test
    void decrypt_rejects_too_short() {
        CryptoService c = serviceV1();
        assertThrows(CryptoException.class, () -> c.decrypt("v1:AAAA"));
    }

    @Test
    void tamperDetection() {
        CryptoService c = serviceV1();
        String cipher = c.encrypt("secret");
        char last = cipher.charAt(cipher.length() - 1);
        String tampered = cipher.substring(0, cipher.length() - 1)
                        + (last == 'A' ? 'B' : 'A');
        assertThrows(CryptoException.class, () -> c.decrypt(tampered));
    }

    @Test
    void wrong_key_cannot_decrypt() {
        CryptoService v1 = serviceV1();

        byte[] otherKey = new byte[32];
        for (int i = 0; i < otherKey.length; i++) otherKey[i] = 0x02;
        SecretKey wrong = new SecretKeySpec(otherKey, "AES");
        CryptoService bad = new CryptoService(() -> wrong, CryptoConfig.defaults());

        String cipher = v1.encrypt("1000");
        assertThrows(CryptoException.class, () -> bad.decrypt(cipher));
    }
    @Test
    void fixture_is_consistent() throws Exception {
        String[] lines = lireFixture("/fixture-v1.txt");
        CryptoService c = serviceV1();
        assertEquals(lines[0], c.decrypt(lines[1]));
    }
    private static String[] lireFixture(String resource) throws Exception {
        try (java.io.InputStream in = CryptoServiceTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture introuvable dans le classpath : " + resource);
            }
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            String content = new String(buf.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.trim().split("\\R");
            if (lines.length < 2) {
                throw new IllegalStateException(
                    "Fixture " + resource + " doit contenir 2 lignes (plaintext puis ciphertext)");
            }
            return lines;
        }
    }

	@Test
    void format_and_algorithm_are_stable() {
        CryptoService c = serviceV1();
        String cipher = c.encrypt("3200.50");

        // Le préfixe doit être v1:
        assertTrue(cipher.startsWith("v1:"));

        // La partie Base64 doit décoder au moins IV(12) + tag(16) = 28 octets
        byte[] raw = java.util.Base64.getDecoder()
            .decode(cipher.substring(3));
        assertTrue(raw.length >= 28);

        // Le déchiffrement doit rendre la valeur d'origine
        assertEquals("3200.50", c.decrypt(cipher));
    }
}