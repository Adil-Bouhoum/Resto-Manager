package com.restaurant.service;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.config.ErrorLogger;
import com.restaurant.dao.CommandeDAO;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Commande;
import com.restaurant.model.enums.StatutCommande;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service métier pour gestion de la cuisine (temps réel)
 * Rafraîchissement auto des commandes en attente/préparation
 */
public class CuisineService {

    private EntityManager entityManager;
    private CommandeDAO commandeDAO;
    private ScheduledExecutorService scheduler;
    private final ObservableList<Commande> commandesEnAttente;
    private final ObservableList<Commande> commandesEnPreparation;
    private final ObservableList<Commande> commandesPret;
    private final ObservableList<Commande> commandesServi;

    private static final int REFRESH_INTERVAL = 10; // secondes

    public CuisineService() {
        this.entityManager = DatabaseConfig.getEntityManager();
        this.commandeDAO = new CommandeDAO(entityManager);
        this.scheduler = null;
        this.commandesEnAttente = FXCollections.observableArrayList();
        this.commandesEnPreparation = FXCollections.observableArrayList();
        this.commandesPret = FXCollections.observableArrayList();
        this.commandesServi = FXCollections.observableArrayList();
    }

    /**
     * Méthode utilitaire pour exécuter une opération dans une transaction
     */
    private <T> T executeWithTransaction(String context, TransactionCallback<T> callback)
            throws DatabaseException, ValidationException {
        EntityTransaction transaction = null;
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();

            T result = callback.execute();

            transaction.commit();
            return result;

        } catch (ValidationException ve) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw ve;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            ErrorLogger.logError(context, e);
            throw new DatabaseException("Erreur lors de l'opération: " + context, e);
        }
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T execute() throws Exception;
    }

    // ==================== CHARGEMENT INITIAL ====================

    /**
     * Charge les commandes en attente
     */
    public void loadCommandesEnAttente() throws DatabaseException {
        try {
            EntityManager localEM = DatabaseConfig.getEntityManager();
            try {
                CommandeDAO localDAO = new CommandeDAO(localEM);
                List<Commande> commandes = localDAO.findByStatut(StatutCommande.EN_ATTENTE);
                Platform.runLater(() -> commandesEnAttente.setAll(commandes));
            } finally {
                if (localEM != null && localEM.isOpen()) {
                    localEM.close();
                }
            }
        } catch (DatabaseException e) {
            ErrorLogger.logError("CuisineService.loadCommandesEnAttente", e);
            throw e;
        }
    }

    /**
     * Charge les commandes en préparation
     */
    public void loadCommandesEnPreparation() throws DatabaseException {
        try {
            EntityManager localEM = DatabaseConfig.getEntityManager();
            try {
                CommandeDAO localDAO = new CommandeDAO(localEM);
                List<Commande> commandes = localDAO.findByStatut(StatutCommande.EN_PREPARATION);
                Platform.runLater(() -> commandesEnPreparation.setAll(commandes));
            } finally {
                if (localEM != null && localEM.isOpen()) {
                    localEM.close();
                }
            }
        } catch (DatabaseException e) {
            ErrorLogger.logError("CuisineService.loadCommandesEnPreparation", e);
            throw e;
        }
    }


    // ==================== OBSERVABLE LISTS ====================

    /**
     * Obtient la liste observable des commandes en attente
     */
    public ObservableList<Commande> getCommandesEnAttenteList() {
        return commandesEnAttente;
    }

    /**
     * Obtient la liste observable des commandes en préparation
     */
    public ObservableList<Commande> getCommandesEnPreparationList() {
        return commandesEnPreparation;
    }

    /**
     * ✅ Obtient la liste observable des commandes prêtes
     */
    public ObservableList<Commande> getCommandesPretList() {
        return commandesPret;
    }

    /**
     * ✅ Obtient la liste observable des commandes servies
     */
    public ObservableList<Commande> getCommandesServiList() {
        return commandesServi;
    }


    // ==================== DÉMARRAGE RAFRAÎCHISSEMENT AUTO ====================

    /**
     * Démarre le rafraîchissement automatique (appelé au démarrage de l'écran)
     */
    public void startAutoRefresh() {
        // ✅ Si scheduler existe et est arrêté, le recréer
        if (scheduler == null || scheduler.isShutdown()) {
            System.out.println("[DEBUG] CuisineService - Création nouveau scheduler");
            scheduler = Executors.newScheduledThreadPool(1);
        }

        scheduler.scheduleAtFixedRate(
                this::refreshAll,
                1,
                REFRESH_INTERVAL,
                TimeUnit.SECONDS
        );
    }

    /**
     * Arrête le rafraîchissement (appelé à la fermeture)
     */
    public void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            System.out.println("[DEBUG] CuisineService - Arrêt du scheduler");
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Rafraîchit toutes les listes en temps réel
     */

    private void refreshAll() {
        try {
            EntityManager localEM = DatabaseConfig.getEntityManager();
            try {
                CommandeDAO localDAO = new CommandeDAO(localEM);

                List<Commande> attente = localDAO.findByStatut(StatutCommande.EN_ATTENTE);
                List<Commande> preparation = localDAO.findByStatut(StatutCommande.EN_PREPARATION);
                List<Commande> pret = localDAO.findByStatut(StatutCommande.PRET);
                List<Commande> servi = localDAO.findByStatut(StatutCommande.SERVI);

                Platform.runLater(() -> {
                    commandesEnAttente.setAll(attente);
                    commandesEnPreparation.setAll(preparation);
                    commandesPret.setAll(pret);
                    commandesServi.setAll(servi);
                });
            } finally {
                if (localEM != null && localEM.isOpen()) {
                    localEM.close();
                }
            }
        } catch (Exception e) {
            ErrorLogger.logError("CuisineService.refreshAll", e);
        }
    }

    // ==================== TRANSITIONS STATUT ====================

    /**
     * Envoie une commande en préparation
     */
    public void envoyerEnPreparation(Commande commande) throws DatabaseException, ValidationException {
        if (commande == null) {
            throw new ValidationException("Commande invalide");
        }

        if (!commande.getStatut().equals(StatutCommande.EN_ATTENTE)) {
            throw new ValidationException(
                    "Seules les commandes en attente peuvent être envoyées en préparation"
            );
        }

        executeWithTransaction("CuisineService.envoyerEnPreparation", () -> {
            commande.setStatut(StatutCommande.EN_PREPARATION);
            Commande saved = commandeDAO.save(commande);

            // Rafraîchir immédiatement
            Platform.runLater(() -> refreshAll());

            return saved;
        });
    }

    /**
     * Marque une commande comme prête
     */
    public void marquerPrete(Commande commande) throws DatabaseException, ValidationException {
        if (commande == null) {
            throw new ValidationException("Commande invalide");
        }

        if (!commande.getStatut().equals(StatutCommande.EN_PREPARATION)) {
            throw new ValidationException(
                    "Seules les commandes en préparation peuvent être marquées comme prêtes"
            );
        }

        executeWithTransaction("CuisineService.marquerPrete", () -> {
            commande.setStatut(StatutCommande.PRET);
            Commande saved = commandeDAO.save(commande);

            Platform.runLater(() -> refreshAll());

            return saved;
        });
    }

    /**
     * Marque une commande comme servie
     */
    public void marquerServie(Commande commande) throws DatabaseException, ValidationException {
        if (commande == null) {
            throw new ValidationException("Commande invalide");
        }

        if (!commande.getStatut().equals(StatutCommande.PRET)) {
            throw new ValidationException(
                    "Seules les commandes prêtes peuvent être marquées comme servies"
            );
        }

        executeWithTransaction("CuisineService.marquerServie", () -> {
            commande.setStatut(StatutCommande.SERVI);
            Commande saved = commandeDAO.save(commande);

            Platform.runLater(() -> refreshAll());

            return saved;
        });
    }

    /**
     * Annule une commande (cuisine)
     */
    public void annulerCommande(Commande commande) throws DatabaseException, ValidationException {
        if (commande == null) {
            throw new ValidationException("Commande invalide");
        }

        if (!commande.getStatut().estEnCours()) {
            throw new ValidationException(
                    "Seules les commandes en cours peuvent être annulées"
            );
        }

        executeWithTransaction("CuisineService.annulerCommande", () -> {
            commande.setStatut(StatutCommande.ANNULEE);
            Commande saved = commandeDAO.save(commande);

            Platform.runLater(() -> refreshAll());

            return saved;
        });
    }

    // ==================== STATISTIQUES ====================

    /**
     * Compte les commandes en attente
     */
    public int countEnAttente() {
        return commandesEnAttente.size();
    }

    /**
     * Compte les commandes en préparation
     */
    public int countEnPreparation() {
        return commandesEnPreparation.size();
    }

    /**
     * Compte les commandes prêtes
     */
    public int countPret() {
        return commandesPret.size();
    }

    /**
     * Compte les commandes servies
     */
    public int countServi() {
        return commandesServi.size();
    }

    /**
     * Temps moyen de préparation (en minutes)
     */
    public double getTempsPreparationMoyen() {
        if (commandesEnPreparation.isEmpty()) {
            return 0.0;
        }

        return commandesEnPreparation.stream()
                .mapToDouble(c -> {
                    LocalDateTime debut = c.getDateCreation();
                    if (debut == null) {
                        return 0.0;
                    }
                    long minutes = ChronoUnit.MINUTES.between(debut, LocalDateTime.now());
                    return (double) minutes;
                })
                .average()
                .orElse(0.0);
    }

    /**
     * Temps d'attente moyen (en minutes)
     */
    public double getTempsAttenteMoyen() {
        if (commandesEnAttente.isEmpty()) {
            return 0.0;
        }

        return commandesEnAttente.stream()
                .mapToDouble(c -> {
                    LocalDateTime creation = c.getDateCreation();
                    if (creation == null) {
                        return 0.0;
                    }
                    long minutes = ChronoUnit.MINUTES.between(creation, LocalDateTime.now());
                    return (double) minutes;
                })
                .average()
                .orElse(0.0);
    }

    /**
     * Récupère une commande par ID (pour détails)
     */
    public Commande getCommandeById(Long id) throws DatabaseException {
        try {
            return commandeDAO.findById(id);
        } catch (DatabaseException e) {
            ErrorLogger.logError("CuisineService.getCommandeById - ID:" + id, e);
            throw e;
        }
    }

    /**
     * Récupère toutes les commandes (pour export/rapport)
     */
    public List<Commande> getAllCommandes() throws DatabaseException {
        return commandeDAO.findAll();
    }

    /**
     * Récupère les commandes par statut
     */
    public List<Commande> getCommandesByStatut(StatutCommande statut) throws DatabaseException {
        return commandeDAO.findByStatut(statut);
    }

    /**
     * Récupère les commandes urgentes (attente > 10 minutes)
     */
    public List<Commande> getCommandesUrgentes() throws DatabaseException {
        List<Commande> enAttente = commandeDAO.findByStatut(StatutCommande.EN_ATTENTE);
        LocalDateTime maintenant = LocalDateTime.now();

        return enAttente.stream()
                .filter(c -> {
                    if (c.getDateCreation() == null) {
                        return false;
                    }
                    long minutes = ChronoUnit.MINUTES.between(c.getDateCreation(), maintenant);
                    return minutes > 10; // Plus de 10 minutes d'attente
                })
                .toList();
    }

    /**
     * Méthode pour nettoyer les ressources
     */
    public void cleanup() {
        stopAutoRefresh();
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
    }
}