package com.example.legacy.listener;

import com.example.crypto.CryptoBootstrap;
import com.example.crypto.CryptoConfig;
import com.example.crypto.EnvKeyProvider;
import com.example.crypto.KeyProvider;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.InputStream;
import java.util.Properties;

/**
 * Initialise le service cryptographique partagé au démarrage de l'application.
 * <p>
 * Le listener est déclaré uniquement dans {@code web.xml} : l'annotation
 * {@code @WebListener} a été retirée pour éviter une double invocation
 * (et donc l'exception « CryptoService déjà initialisé »).
 */
public class CryptoContextListener implements ServletContextListener {

    private static final String CONFIG_RESOURCE = "crypto.properties";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        CryptoConfig config = loadConfig(event);

        try {
            KeyProvider provider = new EnvKeyProvider(config);
            CryptoBootstrap.initGlobal(config, provider);
            event.getServletContext().log(
                    "CryptoService initialisé (algorithme=" + config.getTransformation()
                            + ", version=" + config.getFormatVersion() + ")");
        } catch (RuntimeException e) {
            // L'écran de consultation des exécutions batch ne dépend pas du
            // chiffrement : on n'empêche pas l'application de démarrer si la
            // clé n'est pas fournie, mais l'erreur est tracée explicitement.
            event.getServletContext().log(
                    "CryptoService non initialisé : " + e.getMessage()
                            + " (les accès à la table person seront indisponibles)", e);
        }
    }

    /**
     * Aucun nettoyage nécessaire : le service crypto est sans état externe.
     * <p>
     * La méthode est déclarée explicitement car l'API Servlet 3.1 (Jetty 9.4)
     * la définit comme abstraite : sans elle, l'arrêt/le rechargement du
     * contexte lève un {@code AbstractMethodError}.
     */
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // rien à libérer
    }

    private CryptoConfig loadConfig(ServletContextEvent event) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream input = classLoader.getResourceAsStream(CONFIG_RESOURCE)) {
            if (input == null) {
                event.getServletContext().log(
                        CONFIG_RESOURCE + " absent : utilisation de la configuration par défaut");
                return CryptoConfig.defaults();
            }

            Properties properties = new Properties();
            properties.load(input);

            return new CryptoConfig(
                    properties.getProperty("crypto.transformation",
                            CryptoConfig.DEFAULT_TRANSFORMATION),
                    properties.getProperty("crypto.key-environment-variable",
                            CryptoConfig.DEFAULT_KEY_ENV_VAR),
                    Integer.parseInt(properties.getProperty("crypto.iv-length",
                            String.valueOf(CryptoConfig.DEFAULT_IV_LENGTH)).trim()),
                    Integer.parseInt(properties.getProperty("crypto.tag-length",
                            String.valueOf(CryptoConfig.DEFAULT_TAG_LENGTH)).trim()),
                    properties.getProperty("crypto.format-version",
                            CryptoConfig.DEFAULT_FORMAT_VERSION));
        } catch (Exception e) {
            event.getServletContext().log(
                    "Configuration cryptographique invalide : utilisation des valeurs par défaut", e);
            return CryptoConfig.defaults();
        }
    }
}
