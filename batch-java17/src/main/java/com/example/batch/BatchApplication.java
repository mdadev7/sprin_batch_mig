package com.example.batch;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Point d'entrée du batch de migration.
 * <p>
 * La clé de chiffrement est résolue par {@code EnvKeyProvider}, qui lit d'abord
 * la variable d'environnement puis la propriété système. Les deux blocs
 * ci-dessous alimentent cette propriété avant le démarrage de Spring :
 * <ul>
 *   <li>{@code batch-config.properties} (classpath, développement) ;</li>
 *   <li>{@code .env} (répertoire courant ou {@code batch-java17/}, optionnel).</li>
 * </ul>
 * En production, la variable d'environnement OS reste prioritaire.
 */
@SpringBootApplication
public class BatchApplication {

    private static final Logger log = LoggerFactory.getLogger(BatchApplication.class);

    private static final String APP_CRYPTO_KEY = "APP_CRYPTO_KEY";

    static {
        // Exécuté avant le main : la clé est disponible dès la création des beans.
        loadBatchConfigProperties();
    }

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(BatchApplication.class, args);
    }

    /** Charge la clé depuis batch-config.properties (dev), sans masquer l'environnement OS. */
    private static void loadBatchConfigProperties() {
        try (InputStream input = BatchApplication.class.getClassLoader()
                .getResourceAsStream("batch-config.properties")) {

            if (input == null) {
                log.debug("batch-config.properties absent du classpath.");
                return;
            }
            Properties properties = new Properties();
            properties.load(input);
            putSystemPropertyIfAbsent(APP_CRYPTO_KEY, properties.getProperty(APP_CRYPTO_KEY));
        } catch (Exception e) {
            log.warn("Lecture de batch-config.properties impossible : {}", e.getMessage());
        }
    }

    /** Charge les fichiers .env locaux (répertoire courant puis module batch). */
    private static void loadDotenv() {
        for (String directory : new String[] {".", "batch-java17"}) {
            try {
                Dotenv dotenv = Dotenv.configure()
                        .directory(directory)
                        .ignoreIfMissing()
                        .load();
                dotenv.entries().forEach(entry ->
                        putSystemPropertyIfAbsent(entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                log.debug(".env introuvable dans '{}' : {}", directory, e.getMessage());
            }
        }
    }

    private static void putSystemPropertyIfAbsent(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        // Une variable d'environnement OS réelle reste prioritaire.
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
