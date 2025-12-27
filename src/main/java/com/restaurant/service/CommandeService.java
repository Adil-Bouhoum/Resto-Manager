package com.restaurant.service;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.config.ErrorLogger;
import com.restaurant.dao.*;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Commande;
import com.restaurant.model.LigneCommande;
import com.restaurant.model.Plat;
import com.restaurant.model.enums.StatutCommande;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.List;

public class CommandeService {

    private EntityManager entityManager;
    private CommandeDAO commandeDAO;
    private PlatDAO platDAO;
    private CategorieDAO categorieDAO;
    private TableDAO tableDAO;
    private PaiementDAO paiementDAO;

    public CommandeService() {
        this.entityManager = DatabaseConfig.getEntityManager();
        this.commandeDAO = new CommandeDAO(entityManager);
        this.platDAO = new PlatDAO(entityManager);
        this.categorieDAO = new CategorieDAO(entityManager);
        this.tableDAO = new TableDAO(entityManager);
        this.paiementDAO = new PaiementDAO(entityManager);
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

    /**
     * Ajoute une ligne de commande
     */
    public void addLigneCommande(Commande commande, Plat plat, Integer quantite)
            throws ValidationException, DatabaseException {

        // Validation
        if (commande == null) {
            throw new ValidationException("Commande requise");
        }
        if (plat == null) {
            throw new ValidationException("Plat requis");
        }
        if (quantite == null || quantite <= 0) {
            throw new ValidationException("Quantité doit être > 0");
        }

        // Vérifier statut commande (uniquement EN_ATTENTE)
        if (!commande.getStatut().equals(StatutCommande.EN_ATTENTE)) {
            throw new ValidationException(
                    "Impossible d'ajouter des plats, commande statut: " + commande.getStatut()
            );
        }

        executeWithTransaction("CommandeService.addLigneCommande", () -> {
            // Vérifier si le plat existe déjà dans la commande
            for (LigneCommande ligneExistante : commande.getLignes()) {
                if (ligneExistante.getPlat().getId().equals(plat.getId())) {
                    // Mettre à jour la quantité
                    ligneExistante.setQuantite(ligneExistante.getQuantite() + quantite);
                    commandeDAO.save(commande);
                    return null;
                }
            }

            // Créer nouvelle ligne
            LigneCommande ligne = new LigneCommande();
            ligne.setCommande(commande);
            ligne.setPlat(plat);
            ligne.setQuantite(quantite);
            ligne.setPrix(plat.getPrix()); // Snapshot du prix au moment de la commande

            commande.getLignes().add(ligne);
            commandeDAO.save(commande);

            return null;
        });
    }

    /**
     * Retire une ligne de commande
     */
    public void removeLigneCommande(Commande commande, Long ligneId)
            throws ValidationException, DatabaseException {

        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        if (!commande.getStatut().equals(StatutCommande.EN_ATTENTE)) {
            throw new ValidationException("Impossible de retirer des plats");
        }

        executeWithTransaction("CommandeService.removeLigneCommande", () -> {
            commande.getLignes().removeIf(ligne -> ligne.getId().equals(ligneId));
            commandeDAO.save(commande);
            return null;
        });
    }

    /**
     * Modifie la quantité d'une ligne
     */
    public void updateLigneQuantite(Commande commande, Long ligneId, Integer nouvelleQuantite)
            throws ValidationException, DatabaseException {

        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        if (!commande.getStatut().equals(StatutCommande.EN_ATTENTE)) {
            throw new ValidationException("Impossible de modifier les plats");
        }

        if (nouvelleQuantite == null || nouvelleQuantite <= 0) {
            throw new ValidationException("Quantité doit être > 0");
        }

        executeWithTransaction("CommandeService.updateLigneQuantite", () -> {
            for (LigneCommande ligne : commande.getLignes()) {
                if (ligne.getId().equals(ligneId)) {
                    ligne.setQuantite(nouvelleQuantite);
                    commandeDAO.save(commande);
                    break;
                }
            }
            return null;
        });
    }

    /**
     * Calcule le total d'une commande (avant remise)
     */
    public Double calculateTotal(Commande commande) {
        if (commande == null || commande.getLignes() == null) {
            return 0.0;
        }

        return commande.getLignes().stream()
                .mapToDouble(l -> l.getPrix() * l.getQuantite())
                .sum();
    }

    /**
     * Calcule le total avec remise
     */
    public Double calculateTotalAvecRemise(Commande commande) {
        Double total = calculateTotal(commande);
        Double remise = commande.getRemiseAppliquee() != null ? commande.getRemiseAppliquee() : 0.0;
        return Math.max(0, total - remise);
    }

    /**
     * Applique une remise (max 50% du total)
     */
    public void applyDiscount(Commande commande, Double discount)
            throws ValidationException, DatabaseException {

        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        Double total = calculateTotal(commande);
        Double maxDiscount = total * 0.5; // 50% max

        if (discount < 0) {
            throw new ValidationException("Remise ne peut être négative");
        }

        if (discount > maxDiscount) {
            throw new ValidationException(
                    String.format("Remise max: %.2f€ (50%% du total %.2f€)", maxDiscount, total)
            );
        }

        executeWithTransaction("CommandeService.applyDiscount", () -> {
            commande.setRemiseAppliquee(discount);
            commandeDAO.save(commande);
            return null;
        });
    }

    /**
     * Transition de statut (workflow strict)
     */
    public void updateCommandeStatus(Commande commande, StatutCommande newStatut)
            throws ValidationException, DatabaseException {

        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        StatutCommande current = commande.getStatut();
        boolean validTransition = false;

        // Workflow strict
        switch (current) {
            case EN_ATTENTE:
                validTransition = newStatut.equals(StatutCommande.EN_PREPARATION);
                break;
            case EN_PREPARATION:
                validTransition = newStatut.equals(StatutCommande.PRET);
                break;
            case PRET:
                validTransition = newStatut.equals(StatutCommande.SERVI);
                break;
            case SERVI:
                validTransition = newStatut.equals(StatutCommande.PAYEE);
                break;
            case PAYEE:
                validTransition = newStatut.equals(StatutCommande.FINALISEE);
                break;
            case FINALISEE:
                throw new ValidationException("Commande déjà finalisée");
        }

        if (!validTransition) {
            throw new ValidationException(
                    String.format("Transition interdite: %s → %s", current, newStatut)
            );
        }

        // Vérifications supplémentaires
        if (newStatut.equals(StatutCommande.EN_PREPARATION) && commande.getLignes().isEmpty()) {
            throw new ValidationException("Commande vide, impossible de passer en préparation");
        }

        if (newStatut.equals(StatutCommande.PAYEE) && calculateTotalAvecRemise(commande) <= 0) {
            throw new ValidationException("Total à payer doit être > 0");
        }

        executeWithTransaction("CommandeService.updateCommandeStatus", () -> {
            commande.setStatut(newStatut);
            commandeDAO.save(commande);
            return null;
        });
    }

    /**
     * Récupère une commande par ID
     */
    public Commande getCommandeById(Long id) throws DatabaseException {
        return commandeDAO.findById(id);
    }

    /**
     * Annule une commande (uniquement si EN_ATTENTE)
     */
    public void annulerCommande(Commande commande) throws ValidationException, DatabaseException {
        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        if (!commande.getStatut().equals(StatutCommande.EN_ATTENTE)) {
            throw new ValidationException(
                    "Annulation impossible, commande statut: " + commande.getStatut()
            );
        }

        executeWithTransaction("CommandeService.annulerCommande", () -> {
            commandeDAO.delete(commande.getId());
            return null;
        });
    }

    /**
     * Récupère toutes les commandes avec un statut donné
     */
    public List<Commande> getCommandesByStatut(StatutCommande statut) throws DatabaseException {
        return commandeDAO.findByStatut(statut);
    }

    /**
     * Récupère toutes les commandes (pour rapports)
     */
    public List<Commande> getAllCommandes() throws DatabaseException {
        return commandeDAO.findAll();
    }

    /**
     * Récupère les commandes actives d'une table
     */
    public List<Commande> getCommandesByTable(Long tableId) throws DatabaseException {
        return commandeDAO.findByTableId(tableId);
    }
}