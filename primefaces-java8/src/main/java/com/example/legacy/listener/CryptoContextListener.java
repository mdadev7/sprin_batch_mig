package com.example.legacy.listener;

import com.example.crypto.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import java.io.InputStream;
import java.util.Properties;

@WebListener
public class CryptoContextListener
        implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {

        try (InputStream input =
                Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("crypto.properties")) {

            if (input == null) {
                throw new IllegalStateException(
                        "crypto.properties introuvable");
            }

            Properties properties = new Properties();
            properties.load(input);

            CryptoConfig config = new CryptoConfig(
                    properties.getProperty(
                            "crypto.transformation"),
                    properties.getProperty(
                            "crypto.key-environment-variable"),
                    Integer.parseInt(properties.getProperty(
                            "crypto.iv-length")),
                    Integer.parseInt(properties.getProperty(
                            "crypto.tag-length")),
                    properties.getProperty(
                            "crypto.format-version")
            );

            KeyProvider provider = new EnvKeyProvider(config);

            CryptoService service =
                    new CryptoService(provider, config);

            CryptoHolder.initialize(service);

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Erreur initialisation cryptographique", e);
        }
    }
}