import os
import zipfile

files = {
    "pom.xml": """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>migration-crypto</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>crypto-core</module>
        <module>batch-java17</module>
        <module>primefaces-java8</module>
    </modules>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
""",
    "README.md": """# Migration Crypto Project

Ce projet gère la couche de chiffrement/déchiffrement commune (`crypto-core`) partageable entre des applications legacy Java 8 (`primefaces-java8`) et des batchs modernes Java 17 (`batch-java17`).
""",
    ".env.example": """# Clé secrète AES encodée en Base64 (16, 24 ou 32 octets)
APP_SECRET_KEY=12345678901234567890123456789012
""",
    "crypto-core/pom.xml": """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>migration-crypto</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>crypto-core</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.9.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
""",
    "crypto-core/src/main/java/com/example/crypto/KeyProvider.java": """package com.example.crypto;

public interface KeyProvider {
    byte[] getSecretKey();
}
""",
    "crypto-core/src/main/java/com/example/crypto/EnvKeyProvider.java": """package com.example.crypto;

import java.util.Base64;

public class EnvKeyProvider implements KeyProvider {
    private final String envName;

    public EnvKeyProvider(String envName) {
        this.envName = envName;
    }

    @Override
    public byte[] getSecretKey() {
        String val = System.getenv(envName);
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalStateException("La variable d'environnement '" + envName + "' n'est pas définie.");
        }
        return Base64.getDecoder().decode(val.trim());
    }
}
""",
    "crypto-core/src/main/java/com/example/crypto/CryptoService.java": """package com.example.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final KeyProvider keyProvider;

    public CryptoService(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(keyProvider.getSecretKey(), "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chiffrement", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            byte[] cipherText = new byte[decoded.length - iv.length];
            System.arraycopy(decoded, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(keyProvider.getSecretKey(), "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du déchiffrement", e);
        }
    }
}
""",
    "crypto-core/src/main/java/com/example/crypto/CryptoHolder.java": """package com.example.crypto;

public class CryptoHolder {
    private static CryptoService instance;

    public static synchronized void init(CryptoService service) {
        instance = service;
    }

    public static CryptoService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CryptoHolder n'a pas été initialisé.");
        }
        return instance;
    }
}
""",
    "crypto-core/src/main/java/com/example/crypto/CryptoBootstrap.java": """package com.example.crypto;

public class CryptoBootstrap {
    public static void initialize(String envVarName) {
        KeyProvider provider = new EnvKeyProvider(envVarName);
        CryptoService service = new CryptoService(provider);
        CryptoHolder.init(service);
    }
}
""",
    "crypto-core/src/test/java/com/example/crypto/CryptoServiceTest.java": """package com.example.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    @Test
    void testEncryptAndDecrypt() {
        byte[] keyBytes = "1234567890123456".getBytes();
        KeyProvider provider = () -> keyBytes;

        CryptoService service = new CryptoService(provider);
        String original = "Message secret de migration";

        String encrypted = service.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = service.decrypt(encrypted);
        assertEquals(original, decrypted);
    }
}
""",
    "batch-java17/pom.xml": """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>migration-crypto</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>batch-java17</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>crypto-core</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
""",
    "batch-java17/src/main/resources/application.yml": """crypto:
  env-var: APP_SECRET_KEY
""",
    "primefaces-java8/pom.xml": """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>migration-crypto</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>primefaces-java8</artifactId>
    <packaging>war</packaging>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>crypto-core</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
""",
}

root_dir = "migration-crypto"
zip_filename = "migration-crypto.zip"

with zipfile.ZipFile(zip_filename, "w", zipfile.ZIP_DEFLATED) as zip_file:
    for path, content in files.items():
        zip_path = os.path.join(root_dir, path)
        zip_file.writestr(zip_path, content)

    # Dossiers vides
    zip_file.writestr(
        f"{root_dir}/batch-java17/src/main/java/com/example/batch/.gitkeep", ""
    )
    zip_file.writestr(
        f"{root_dir}/primefaces-java8/src/main/java/com/example/legacy/.gitkeep",
        "",
    )
    zip_file.writestr(
        f"{root_dir}/primefaces-java8/src/main/resources/META-INF/.gitkeep", ""
    )
    zip_file.writestr(
        f"{root_dir}/primefaces-java8/src/main/webapp/.gitkeep", ""
    )

print(f"Archive générée avec succès : {zip_filename}")