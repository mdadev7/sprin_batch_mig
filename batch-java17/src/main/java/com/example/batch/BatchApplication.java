package com.example.batch;
 

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;

import com.example.crypto.CryptoConfig;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class BatchApplication {
	  // 1. Ce bloc s'exécute AVANT le main et AVANT tout chargement de Spring
    static {
        try (InputStream input = BatchApplication.class.getClassLoader()
                .getResourceAsStream("batch-config.properties")) {
            
            if (input != null) {
                Properties prop = new Properties();
                // Charge uniquement le fichier de configuration en mémoire
                prop.load(input);
                
                String cryptoKey = prop.getProperty("APP_CRYPTO_KEY");
                if (cryptoKey != null) {
                    // Injecte la clé dans les propriétés système de la JVM
                    System.setProperty("APP_CRYPTO_KEY", cryptoKey);
                }
            } else {
                System.out.println("⚠️ batch-config.properties introuvable dans les ressources.");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du chargement de batch-config.properties : " + e.getMessage());
        }
    }

    // 2. Le main démarre ensuite de manière classique
    
    public static void main001(String[] args) {
        SpringApplication application = new SpringApplication(BatchApplication.class);

        // On ajoute un initialiseur qui s'exécute AVANT la création des beans
        application.addInitializers((ApplicationContextInitializer<ConfigurableApplicationContext>) context -> {
            try {
                // 1. Lire directement le fichier batch-config.properties depuis le classpath
                ResourcePropertySource propertySource = new ResourcePropertySource("classpath:batch-config.properties");
                
                // 2. Extraire la valeur de APP_CRYPTO_KEY
                String cryptoKey = (String) propertySource.getProperty("APP_CRYPTO_KEY");

                log.info("#############################@   APP_CRYPTO_KEY    = {}", cryptoKey);
                // 3. L'injecter immédiatement dans la JVM avant que EnvKeyProvider ne démarre
                if (cryptoKey != null) {
                    System.setProperty("APP_CRYPTO_KEY", cryptoKey);
                }
            } catch (IOException e) {
                // Le fichier est absent (ce qui est normal en prod si la variable vient de l'OS)
                System.out.println("batch-config.properties absent, lecture des variables OS classiques.");
            }
        });

        // Lance l'application normalement
        application.run(args);
    }

    public static void main(String[] args) {
    	// 1. Charge le fichier .env de la racine
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing() 
                .load();
        
        // 2. Copie les variables dans System Properties pour que les librairies puissent les lire
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
            // Optionnel : injecte aussi dans l'environnement si la librairie utilise System.getenv()
            // Note: System.setProperty permet à Spring de surcharger System.getenv() sous le capot
        });
     // 1. Démarre Spring et charge le fichier batch-config.properties
        ConfigurableApplicationContext context = SpringApplication.run(BatchApplication.class, args);
        
        // 2. Récupère la clé lue par Spring
        String cryptoKey = context.getEnvironment().getProperty("APP_CRYPTO_KEY");
        
        // 3. L'injecte pour que EnvKeyProvider puisse la trouver via System.getenv()
        if (cryptoKey != null) {
            System.setProperty("APP_CRYPTO_KEY", cryptoKey);
        }
        // 4. Démarre Spring
       // SpringApplication.run(  BatchApplication.class, args);
    }
    private static final Logger log = LoggerFactory.getLogger(BatchApplication.class);
    //@Bean
    CommandLineRunner logCryptoConfig(CryptoConfig config) {
        return args -> {
            log.info("#############################@ Crypto transformation    = {}", config.getTransformation());
            log.info("#############################@ Crypto formatVersion     = {}", config.getFormatVersion());
            log.info("#############################@ Crypto ivLength          = {}", config.getIvLength());
            log.info("#############################@ Crypto tagLength         = {}", config.getTagLength());
            log.info("#############################@ Crypto keyEnvVar         = {}", config.getKeyEnvironmentVariable());
            String v = System.getenv(config.getKeyEnvironmentVariable());
            log.info("#############################@ Crypto key present       = {}", v != null && !v.isBlank());
        };
    }
}


