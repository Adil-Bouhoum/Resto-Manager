package com.restaurant.config;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Singleton pour gérer la connexion à la base de données
 * Crée une EntityManagerFactory au démarrage, la garde en mémoire
 */
public class DatabaseConfig {
    private static EntityManagerFactory emf;
    private static final String PERSISTENCE_UNIT = "restaurantPU";

    static {
        try {
            emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT);
            ErrorLogger.logInfo("✅ Base de données H2 initialisée avec succès");
        } catch (Exception e) {
            ErrorLogger.logError("❌ Erreur initialisation base de données", e);
            throw new ExceptionInInitializerError();
        }
    }

    /**
     * Obtenir une instance EntityManager pour une transaction
     */
    public static EntityManager getEntityManager() {
        if (emf == null || !emf.isOpen()) {
            throw new IllegalStateException("EntityManagerFactory n'est pas initialisée");
        }
        return emf.createEntityManager();
    }

    /**
     * Obtenir la EntityManagerFactory (rare)
     */
    public static EntityManagerFactory getEntityManagerFactory() {
        return emf;
    }

    /**
     * Fermer la factory (appeler une seule fois à l'arrêt de l'app)
     */
    public static void shutdown() {
        try {
            if (emf != null && emf.isOpen()) {
                emf.close();
                ErrorLogger.logInfo("✅ Base de données fermée");
            }
        } catch (Exception e) {
            ErrorLogger.logError("Erreur fermeture base de données", e);
        }
    }

    /**
     * Vérifier que la BD est connectée (utile pour tests)
     */
    public static boolean isConnected() {
        try {
            EntityManager em = getEntityManager();
            em.createQuery("SELECT 1").getSingleResult();
            em.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}