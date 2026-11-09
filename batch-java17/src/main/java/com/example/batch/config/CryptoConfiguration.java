package com.example.batch.config; 

import com.example.crypto.CryptoConfig;
import com.example.crypto.CryptoService;
import com.example.crypto.EnvKeyProvider;
import com.example.crypto.KeyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfiguration {

    @Bean
    public CryptoConfig cryptoConfig(
            @Value("${crypto.transformation:AES/GCM/NoPadding}") String transformation,
            @Value("${crypto.key-environment-variable:APP_CRYPTO_KEY}") String envName,
            @Value("${crypto.iv-length:12}") int ivLength,
            @Value("${crypto.tag-length:128}") int tagLength,
            @Value("${crypto.format-version:v1}") String version) {

        return new CryptoConfig(transformation, envName, ivLength, tagLength, version);
    }

    @Bean
    public KeyProvider keyProvider(CryptoConfig config) {
        return new EnvKeyProvider(config);
    }

    @Bean
    public CryptoService cryptoService(KeyProvider provider, CryptoConfig config) {
        return new CryptoService(provider, config);
    }
}