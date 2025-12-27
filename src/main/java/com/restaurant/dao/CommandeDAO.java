package com.restaurant.dao;

import com.restaurant.config.ErrorLogger;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Commande;
import com.restaurant.model.TableResto;
import com.restaurant.model.enums.StatutCommande;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class CommandeDAO extends GenericDAO<Commande> {

    public CommandeDAO(EntityManager entityManager) {
        super(entityManager, Commande.class);
    }

    /**
     * Recherche les commandes par statut
     */
    public List<Commande> findByStatut(StatutCommande statut) throws DatabaseException {
        try {
            String query = "SELECT DISTINCT c FROM Commande c " +
                    "LEFT JOIN FETCH c.table " +              // ✅ Charger table
                    "LEFT JOIN FETCH c.lignes l " +           // ✅ Charger lignes
                    "LEFT JOIN FETCH l.plat " +               // ✅ Charger plats des lignes
                    "WHERE c.statut = :statut " +
                    "ORDER BY c.dateCreation";

            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("statut", statut);

            return q.getResultList();
        } catch (Exception e) {
            ErrorLogger.logError("CommandeDAO.findByStatut - Statut: " + statut, e);
            throw new DatabaseException("Erreur recherche commandes par statut", e);
        }
    }
    /**
     * Recherche les commandes par table (objet TableResto)
     */
    public List<Commande> findByTable(TableResto table) throws DatabaseException {
        try {
            String query = "FROM Commande c WHERE c.table = :table ORDER BY c.dateCreation DESC";
            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("table", table);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche commandes par table", e);
        }
    }

    /**
     * Recherche les commandes par ID de table
     * @param tableId L'ID de la table
     * @return Liste des commandes pour cette table
     * @throws DatabaseException
     */
    public List<Commande> findByTableId(Long tableId) throws DatabaseException {
        try {
            String query = "FROM Commande c WHERE c.table.id = :tableId ORDER BY c.dateCreation DESC";
            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("tableId", tableId);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche commandes par ID de table: " + tableId, e);
        }
    }

    /**
     * Recherche les commandes actives par ID de table
     * (commandes non finalisées ni annulées)
     */
    public List<Commande> findActiveByTableId(Long tableId) throws DatabaseException {
        try {
            String query = "FROM Commande c WHERE c.table.id = :tableId " +
                    "AND c.statut NOT IN (:finalStatuses) " +
                    "ORDER BY c.dateCreation DESC";
            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("tableId", tableId);
            q.setParameter("finalStatuses", List.of(StatutCommande.FINALISEE, StatutCommande.ANNULEE));
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche commandes actives par ID de table: " + tableId, e);
        }
    }

    /**
     * Recherche la commande active (non finalisée) d'une table
     */
    public Commande findActiveCommandeByTableId(Long tableId) throws DatabaseException {
        try {
            String query = "FROM Commande c WHERE c.table.id = :tableId " +
                    "AND c.statut NOT IN (:finalStatuses) " +
                    "ORDER BY c.dateCreation DESC";
            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("tableId", tableId);
            q.setParameter("finalStatuses", List.of(StatutCommande.FINALISEE, StatutCommande.ANNULEE));

            List<Commande> result = q.getResultList();
            return result.isEmpty() ? null : result.get(0);
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche commande active par ID de table: " + tableId, e);
        }
    }

    /**
     * Recherche les commandes par période
     */
    public List<Commande> findByDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate)
            throws DatabaseException {
        try {
            String query = "FROM Commande c WHERE DATE(c.dateCreation) BETWEEN :startDate AND :endDate " +
                    "ORDER BY c.dateCreation DESC";
            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("startDate", startDate);
            q.setParameter("endDate", endDate);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche commandes par période", e);
        }
    }

    /**
     * Recherche les commandes non payées (en attente de paiement)
     */
    public List<Commande> findCommandesNonPayees() throws DatabaseException {
        try {
            String query = "FROM Commande c WHERE c.statut = :servi OR " +
                    "(c.statut = :payee AND c NOT IN (SELECT p.commande FROM Paiement p)) " +
                    "ORDER BY c.dateCreation";
            TypedQuery<Commande> q = entityManager.createQuery(query, Commande.class);
            q.setParameter("servi", StatutCommande.SERVI);
            q.setParameter("payee", StatutCommande.PAYEE);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche commandes non payées", e);
        }
    }

    /**
     * Compte le nombre de commandes par statut
     */
    public long countByStatut(StatutCommande statut) throws DatabaseException {
        try {
            String query = "SELECT COUNT(c) FROM Commande c WHERE c.statut = :statut";
            TypedQuery<Long> q = entityManager.createQuery(query, Long.class);
            q.setParameter("statut", statut);
            return q.getSingleResult();
        } catch (Exception e) {
            throw new DatabaseException("Erreur comptage commandes par statut", e);
        }
    }

    /**
     * Calcule le chiffre d'affaires total
     */
    public Double calculateTotalCA() throws DatabaseException {
        try {
            String query = "SELECT SUM(c.remiseAppliquee) FROM Commande c WHERE c.statut = :payee";
            TypedQuery<Double> q = entityManager.createQuery(query, Double.class);
            q.setParameter("payee", StatutCommande.PAYEE);
            Double result = q.getSingleResult();
            return result != null ? result : 0.0;
        } catch (Exception e) {
            throw new DatabaseException("Erreur calcul CA total", e);
        }
    }

    @Override
    protected void validateEntity(Commande entity) throws ValidationException {
        if (entity.getTable() == null) {
            throw new ValidationException("Une table doit être associée à la commande");
        }
        if (entity.getStatut() == null) {
            throw new ValidationException("Le statut de la commande est requis");
        }
        // Note: On ne valide plus que les lignes ne soient pas vides
        // car une commande peut être créée vide et remplie ensuite
    }
}