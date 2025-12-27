package com.restaurant.service;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.config.ErrorLogger;
import com.restaurant.dao.CommandeDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Commande;
import com.restaurant.model.TableResto;
import com.restaurant.model.enums.StatutCommande;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.util.List;

public class SalleService {

    private EntityManager entityManager;
    private TableDAO tableDAO;
    private CommandeDAO commandeDAO;

    public SalleService() {
        this.entityManager = DatabaseConfig.getEntityManager();
        this.tableDAO = new TableDAO(entityManager);
        this.commandeDAO = new CommandeDAO(entityManager);
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
     * ✅ FIX: Récupère toutes les tables AVEC les commandes chargées
     * IMPORTANT: Crée une NOUVELLE requête pour éviter le cache stale
     */
    public List<TableResto> getAllTables() throws DatabaseException {
        try {
            // ✅ Nettoyer le cache avant de récupérer (important après modifications)
            entityManager.clear();

            String query = "SELECT DISTINCT t FROM TableResto t " +
                    "LEFT JOIN FETCH t.commandes ";
            TypedQuery<TableResto> q = entityManager.createQuery(query, TableResto.class);

            List<TableResto> tables = q.getResultList();

            System.out.println("[DEBUG SalleService.getAllTables] " + tables.size() +
                    " tables récupérées avec " +
                    tables.stream().mapToInt(t -> t.getCommandes().size()).sum() +
                    " commandes totales");

            return tables;
        } catch (Exception e) {
            ErrorLogger.logError("SalleService.getAllTables", e);
            throw new DatabaseException("Erreur récupération tables", e);
        }
    }

    /**
     * Récupère une table par son ID (avec commandes)
     */
    public TableResto getTableById(Long id) throws DatabaseException {
        try {
            entityManager.clear();  // ✅ Nettoyer le cache

            String query = "SELECT t FROM TableResto t " +
                    "LEFT JOIN FETCH t.commandes " +
                    "WHERE t.id = :id";
            TypedQuery<TableResto> q = entityManager.createQuery(query, TableResto.class);
            q.setParameter("id", id);

            List<TableResto> results = q.getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            ErrorLogger.logError("SalleService.getTableById", e);
            throw new DatabaseException("Erreur récupération table", e);
        }
    }

    /**
     * Récupère une table par son numéro (avec commandes)
     */
    public TableResto getTableByNumero(Integer numero) throws DatabaseException {
        try {
            entityManager.clear();  // ✅ Nettoyer le cache

            String query = "SELECT t FROM TableResto t " +
                    "LEFT JOIN FETCH t.commandes " +
                    "WHERE t.numeroTable = :num";
            TypedQuery<TableResto> q = entityManager.createQuery(query, TableResto.class);
            q.setParameter("num", numero);

            List<TableResto> results = q.getResultList();
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            ErrorLogger.logError("SalleService.getTableByNumero", e);
            throw new DatabaseException("Erreur récupération table", e);
        }
    }

    /**
     * Crée une nouvelle table
     */
    public TableResto createTable(Integer numero, Integer capacite)
            throws ValidationException, DatabaseException {

        if (numero == null || numero <= 0) {
            throw new ValidationException("Numéro de table invalide");
        }

        if (capacite == null || capacite <= 0) {
            throw new ValidationException("Capacité invalide");
        }

        // Vérifier si le numéro existe déjà
        TableResto existing = getTableByNumero(numero);
        if (existing != null) {
            throw new ValidationException("Une table avec le numéro " + numero + " existe déjà");
        }

        return executeWithTransaction("SalleService.createTable", () -> {
            TableResto table = new TableResto(numero, capacite);
            return tableDAO.save(table);
        });
    }

    /**
     * Met à jour une table existante
     */
    public TableResto updateTable(Long id, Integer numero, Integer capacite)
            throws ValidationException, DatabaseException {

        TableResto table = getTableById(id);
        if (table == null) {
            throw new ValidationException("Table introuvable");
        }

        if (numero != null && !numero.equals(table.getNumeroTable())) {
            // Vérifier si le nouveau numéro existe déjà
            TableResto existing = getTableByNumero(numero);
            if (existing != null && !existing.getId().equals(id)) {
                throw new ValidationException("Une table avec le numéro " + numero + " existe déjà");
            }
            table.setNumeroTable(numero);
        }

        if (capacite != null && capacite > 0) {
            table.setCapacite(capacite);
        }

        return executeWithTransaction("SalleService.updateTable", () -> {
            return tableDAO.save(table);
        });
    }

    /**
     * Supprime une table (uniquement si elle n'a pas de commandes)
     */
    public void deleteTable(Long id) throws ValidationException, DatabaseException {
        TableResto table = getTableById(id);
        if (table == null) {
            throw new ValidationException("Table introuvable");
        }

        // Vérifier si la table a des commandes
        if (!table.getCommandes().isEmpty()) {
            throw new ValidationException("Impossible de supprimer une table avec des commandes");
        }

        executeWithTransaction("SalleService.deleteTable", () -> {
            tableDAO.delete(id);
            return null;
        });
    }

    /**
     * Récupère le statut d'une table
     * Retourne: "LIBRE", "OCCUPEE", ou "ATTENTE_PAIEMENT"
     */
    public String getTableStatus(TableResto table) throws DatabaseException {
        if (table == null) {
            return "LIBRE";
        }

        // Utiliser la méthode calculée isOccupee()
        if (!table.isOccupee()) {
            return "LIBRE";
        }

        // Vérifier si une commande est en attente de paiement
        List<Commande> commandes = table.getCommandes();
        for (Commande commande : commandes) {
            if (commande.getStatut() == StatutCommande.SERVI) {
                return "ATTENTE_PAIEMENT";
            }
        }

        return "OCCUPEE";
    }

    /**
     * Compte les tables occupées
     */
    public long countTablesOccupees() throws DatabaseException {
        try {
            return getAllTables().stream()
                    .filter(TableResto::isOccupee)
                    .count();
        } catch (DatabaseException e) {
            ErrorLogger.logError("SalleService.countTablesOccupees", e);
            throw e;
        }
    }

    /**
     * Compte les tables libres
     */
    public long countTablesLibres() throws DatabaseException {
        try {
            return getAllTables().stream()
                    .filter(table -> !table.isOccupee())
                    .count();
        } catch (DatabaseException e) {
            ErrorLogger.logError("SalleService.countTablesLibres", e);
            throw e;
        }
    }

    /**
     * Crée une nouvelle commande pour une table
     */
    public Commande startNewCommande(TableResto table)
            throws ValidationException, DatabaseException {

        if (table == null) {
            throw new ValidationException("Table requise");
        }

        // ✅ Recharger la table AVEC ses commandes avant de vérifier
        TableResto refreshedTable = getTableById(table.getId());
        if (refreshedTable == null) {
            throw new ValidationException("Table introuvable");
        }

        // Vérifier si la table est occupée
        if (refreshedTable.isOccupee()) {
            throw new ValidationException("La table est déjà occupée");
        }

        return executeWithTransaction("SalleService.startNewCommande", () -> {
            Commande commande = new Commande(refreshedTable);
            System.out.println("[DEBUG SalleService.startNewCommande] Création commande pour Table " +
                    refreshedTable.getNumeroTable());
            return commandeDAO.save(commande);
        });
    }

    /**
     * Récupère la commande active d'une table
     */
    public Commande getActiveCommande(TableResto table) throws DatabaseException {
        if (table == null) {
            return null;
        }

        List<Commande> commandes = table.getCommandes();
        for (Commande commande : commandes) {
            if (commande.getStatut().estEnCours()) {
                return commande;
            }
        }
        return null;
    }

    /**
     * Libère une table (marque commande comme FINALISEE)
     */
    public void liberateTable(TableResto table) throws ValidationException, DatabaseException {
        Commande commande = getActiveCommande(table);
        if (commande == null) {
            throw new ValidationException("Aucune commande active");
        }

        if (!commande.getStatut().equals(StatutCommande.PAYEE)) {
            throw new ValidationException("La commande doit être payée avant de libérer la table");
        }

        executeWithTransaction("SalleService.liberateTable", () -> {
            commande.setStatut(StatutCommande.FINALISEE);
            commandeDAO.save(commande);
            return null;
        });
    }

    /**
     * ✅ FIX: Récupère toutes les tables avec leur statut
     * Les commandes sont déjà chargées via getAllTables() avec FETCH
     */
    public List<TableAvecStatut> getAllTablesWithStatus() throws DatabaseException {
        List<TableResto> tables = getAllTables();

        System.out.println("[DEBUG SalleService.getAllTablesWithStatus] " + tables.size() + " tables");

        return tables.stream()
                .map(table -> {
                    try {
                        System.out.println("[DEBUG SalleService.getAllTablesWithStatus]   Table " +
                                table.getNumeroTable() + " - " + table.getCommandes().size() +
                                " commande(s)");

                        String status = getTableStatus(table);
                        return new TableAvecStatut(table, status);
                    } catch (DatabaseException e) {
                        ErrorLogger.logError("SalleService.getAllTablesWithStatus", e);
                        return new TableAvecStatut(table, "ERREUR");
                    }
                })
                .toList();
    }

    // ==================== DTO ====================

    /**
     * DTO pour afficher une table avec son statut
     */
    public static class TableAvecStatut {
        private TableResto table;
        private String statut;

        public TableAvecStatut(TableResto table, String statut) {
            this.table = table;
            this.statut = statut;
        }

        public TableResto getTable() {
            return table;
        }

        public String getStatut() {
            return statut;
        }

        public String getNumeroTable() {
            return table != null ? table.getNumeroTable().toString() : "";
        }

        public String getCapacite() {
            return table != null ? table.getCapacite().toString() : "";
        }

        @Override
        public String toString() {
            return "Table " + getNumeroTable() + " - " + statut;
        }
    }
}