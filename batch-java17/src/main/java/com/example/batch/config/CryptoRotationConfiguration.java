package com.example.batch.config;

import com.example.crypto.CryptoConfig;
import com.example.crypto.CryptoService;
import com.example.crypto.EnvKeyProvider;
import com.example.crypto.KeyProvider;
import com.example.crypto.RotatingCryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@Profile("rotation")
public class CryptoRotationConfiguration {

    @Bean
    public RotatingCryptoService rotatingCryptoService(
            @Value("${crypto.current-version}") String currentVersion,
            @Value("${crypto.versions.v1.key-environment-variable}") String v1Var,
            @Value("${crypto.versions.v2.key-environment-variable}") String v2Var) {

        CryptoConfig v1 = new CryptoConfig(
            "AES/GCM/NoPadding", v1Var, 12, 128, "v1");
        CryptoConfig v2 = new CryptoConfig(
            "AES/GCM/NoPadding", v2Var, 12, 128, "v2");

        Map<String, CryptoConfig> configs = new LinkedHashMap<>();
        configs.put("v1", v1);
        configs.put("v2", v2);

        Map<String, KeyProvider> providers = new LinkedHashMap<>();
        providers.put("v1", new EnvKeyProvider(v1));
        providers.put("v2", new EnvKeyProvider(v2));

        Map<String, CryptoService> services = new LinkedHashMap<>();
        services.put("v1", new CryptoService(providers.get("v1"), v1));
        services.put("v2", new CryptoService(providers.get("v2"), v2));

        return new RotatingCryptoService(services, currentVersion);
    }
}
