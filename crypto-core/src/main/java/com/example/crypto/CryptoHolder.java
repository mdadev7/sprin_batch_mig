package com.example.crypto;

/**
 * Point d'accès statique au service cryptographique courant.
 * Initialisé une seule fois au démarrage de l'application
 * (ServletContextListener côté legacy, @Bean côté Spring Boot).
 *
 * Supporte CryptoService simple OU RotatingCryptoService :
 * les deux exposent encrypt/decrypt, on les traite via l'interface commune.
 */
public final class CryptoHolder {

    private static volatile CryptoService simple;
    private static volatile RotatingCryptoService rotating;

    private CryptoHolder() {}

    public static synchronized void init(CryptoService service) {
        if (simple != null || rotating != null) {
            throw new IllegalStateException("CryptoService déjà initialisé");
        }
        simple = service;
    }

    public static synchronized void initialize(CryptoService service) {
        init(service);
    }

    public static synchronized void init(RotatingCryptoService service) {
        if (simple != null || rotating != null) {
            throw new IllegalStateException("CryptoService déjà initialisé");
        }
        rotating = service;
    }

    public static synchronized void reset() {
        simple = null;
        rotating = null;
    }

    /** True si un service (simple ou rotating) est disponible. */
    public static boolean isInitialized() {
        return simple != null || rotating != null;
    }

    private static void requireInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException(
                "CryptoService non initialisé — appeler CryptoBootstrap.initGlobal(...)");
        }
    }

    public static String encrypt(String plain) {
        requireInitialized();
        return rotating != null ? rotating.encrypt(plain) : simple.encrypt(plain);
    }

    public static String decrypt(String cipher) {
        requireInitialized();
        return rotating != null ? rotating.decrypt(cipher) : simple.decrypt(cipher);
    }

    /** Accès avancé (re-chiffrement, tests). */
    public static RotatingCryptoService rotating() {
        requireInitialized();
        if (rotating == null) throw new IllegalStateException("Mode simple actif");
        return rotating;
    }

    public static CryptoService simple() {
        requireInitialized();
        if (simple == null) throw new IllegalStateException("Mode rotating actif");
        return simple;
    }

    public static CryptoService get() {
        return simple();
    }

    public static CryptoService getInstance() {
        return simple();
    }
}