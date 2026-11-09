package com.example.crypto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fabriques de CryptoService / RotatingCryptoService et point
 * d'initialisation globale.
 *
 * Cas d'usage typiques :
 *  - Nouveau projet : CryptoBootstrap.initGlobal(config, keyProvider)
 *  - Rotation v1 → v2 : CryptoBootstrap.initRotating(v1Config, v1Key,
 *                                                    v2Config, v2Key, "v2")
 */
public final class CryptoBootstrap {

    private CryptoBootstrap() {}

    // ---------- Mode simple (une seule version) ----------

    public static CryptoService create(CryptoConfig config, KeyProvider provider) {
        return new CryptoService(provider, config);
    }

    public static CryptoService create(KeyProvider provider) {
        return create(CryptoConfig.defaults(), provider);
    }

    public static CryptoService initGlobal(CryptoConfig config, KeyProvider provider) {
        CryptoService service = create(config, provider);
        CryptoHolder.init(service);
        return service;
    }

    public static CryptoService initGlobal(KeyProvider provider) {
        return initGlobal(CryptoConfig.defaults(), provider);
    }

    // ---------- Mode rotation (v1 + v2) ----------

    /**
     * Initialise un RotatingCryptoService avec une ou plusieurs versions.
     * La version courante est celle utilisée pour chiffrer.
     *
     * @param configsByVersion  map "v1" -> config, "v2" -> config, ...
     * @param providersByVersion map "v1" -> provider, "v2" -> provider, ...
     * @param currentVersion    version utilisée pour chiffrer (ex. "v2")
     */
    public static RotatingCryptoService initRotating(
            Map<String, CryptoConfig> configsByVersion,
            Map<String, KeyProvider> providersByVersion,
            String currentVersion) {

        if (configsByVersion == null || configsByVersion.isEmpty()) {
            throw new IllegalArgumentException("Aucune config fournie");
        }
        if (providersByVersion == null
                || providersByVersion.size() != configsByVersion.size()) {
            throw new IllegalArgumentException(
                "Providers et configs doivent avoir la même taille");
        }

        Map<String, CryptoService> services = new LinkedHashMap<>();
        for (Map.Entry<String, CryptoConfig> e : configsByVersion.entrySet()) {
            String version = e.getKey();
            KeyProvider provider = providersByVersion.get(version);
            if (provider == null) {
                throw new IllegalArgumentException(
                    "Provider manquant pour la version " + version);
            }
            services.put(version, new CryptoService(provider, e.getValue()));
        }

        RotatingCryptoService rotating =
            new RotatingCryptoService(services, currentVersion);
        CryptoHolder.init(rotating);
        return rotating;
    }

    /** Variante simplifiée pour deux versions. */
    public static RotatingCryptoService initRotating(
            CryptoConfig v1Config, KeyProvider v1Provider,
            CryptoConfig v2Config, KeyProvider v2Provider,
            String currentVersion) {

        Map<String, CryptoConfig> configs = new LinkedHashMap<>();
        configs.put(v1Config.getFormatVersion(), v1Config);
        configs.put(v2Config.getFormatVersion(), v2Config);

        Map<String, KeyProvider> providers = new LinkedHashMap<>();
        providers.put(v1Config.getFormatVersion(), v1Provider);
        providers.put(v2Config.getFormatVersion(), v2Provider);

        return initRotating(configs, providers, currentVersion);
    }
}