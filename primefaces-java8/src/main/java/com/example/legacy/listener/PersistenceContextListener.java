package com.example.legacy.listener;

import com.example.legacy.batch.BatchConsultationDao;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Ferme l'{@code EntityManagerFactory} statique du DAO de consultation à
 * l'arrêt du contexte web, afin d'éviter les fuites de connexions lors des
 * redéploiements à chaud.
 */
public class PersistenceContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        // Rien à faire : la fabrique est créée paresseusement par le DAO.
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        BatchConsultationDao.close();
    }
}
