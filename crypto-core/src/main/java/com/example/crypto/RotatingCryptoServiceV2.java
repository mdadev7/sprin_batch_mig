package com.example.crypto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agrège plusieurs CryptoService (un par version de format) et permet :
 *  - de CHIFFRER avec la version courante (currentVersion) ;
 *  - de DÉCHIFFRER toute valeur dont le préfixe correspond à une version connue.
 *
 * Cas d'usage : rotation de clé sans downtime.
 *  1. Ajouter la config v2 avec sa nouvelle clé, currentVersion = "v2".
 *  2. Déployer : les nouvelles écritures sont en v2, les lectures de v1 continuent.
 *  3. Re-chiffrer les données (job dédié) en v2.
 *  4. Retirer v1 une fois la migration terminée.
 *
 * Le choix du service pour le déchiffrement se fait par correspondance
 * de préfixe — donc pas d'ambiguïté même si deux versions coexistent.
 */
public final class RotatingCryptoServiceV2 {  

    private final Map<String, CryptoService> byVersion;
    private final CryptoService current;
    private final String currentVersion;

    public RotatingCryptoServiceV2(Map<String, CryptoService> services,
                                 String currentVersion) {
        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException("Au moins un CryptoService requis");
        }
        if (currentVersion == null || !services.containsKey(currentVersion)) {
            throw new IllegalArgumentException(
                "Version courante absente du mapping : " + currentVersion);
        }
        this.byVersion = Collections.unmodifiableMap(new LinkedHashMap<>(services));
        this.current = services.get(currentVersion);
        this.currentVersion = currentVersion;
    }

    public String encrypt(String value) { return current.encrypt(value); }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        for (Map.Entry<String, CryptoService> e : byVersion.entrySet()) {
            if (encryptedValue.startsWith(e.getKey() + ":")) {
                return e.getValue().decrypt(encryptedValue);
            }
        }
        throw new CryptoException(
            "Aucune version de chiffrement connue pour cette valeur");
    }

    public CryptoService serviceFor(String version) {
        CryptoService s = byVersion.get(version);
        if (s == null) throw new IllegalArgumentException("Version inconnue : " + version);
        return s;
    }

    public String currentVersion() { return currentVersion; }
}