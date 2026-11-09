package com.example.batch; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.crypto.CryptoConfig;

//@Component
public class CryptoStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CryptoStartupLogger.class);

    private final CryptoConfig cryptoConfig;

    @Value("${batch.input-file}")           private String inputFile;
    @Value("${batch.chunk-size}")           private int chunkSize;
    @Value("${batch.grid-size}")            private int gridSize;
    @Value("${batch.throttle-limit}")       private int throttleLimit;
    @Value("${batch.skip-limit}")           private int skipLimit;
    @Value("${batch.retry-limit}")          private int retryLimit;

    public CryptoStartupLogger(CryptoConfig cryptoConfig) {
        this.cryptoConfig = cryptoConfig;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================================");
        log.info(" Configuration de démarrage");
        log.info("========================================================");
        log.info(" Crypto :");
        log.info("   transformation           = {}", cryptoConfig.getTransformation());
        log.info("   formatVersion            = {}", cryptoConfig.getFormatVersion());
        log.info("   formatPrefix             = {}", cryptoConfig.formatPrefix());
        log.info("   ivLength (octets)        = {}", cryptoConfig.getIvLength());
        log.info("   tagLength (bits)         = {}", cryptoConfig.getTagLength());
        log.info("   keyEnvironmentVariable   = {}", cryptoConfig.getKeyEnvironmentVariable());
        log.info("   clé présente dans l'env  = {}", clePresente());
        log.info(" Batch :");
        log.info("   inputFile                = {}", inputFile);
        log.info("   chunkSize                = {}", chunkSize);
        log.info("   gridSize                 = {}", gridSize);
        log.info("   throttleLimit            = {}", throttleLimit);
        log.info("   skipLimit                = {}", skipLimit);
        log.info("   retryLimit               = {}", retryLimit);
        log.info("========================================================");
    }

    /**
     * Retourne true/false sans jamais afficher la clé.
     */
    private boolean clePresente() {
        String envVar = cryptoConfig.getKeyEnvironmentVariable();
        String value = System.getenv(envVar);
        return value != null && !value.trim().isEmpty();
    }
}
