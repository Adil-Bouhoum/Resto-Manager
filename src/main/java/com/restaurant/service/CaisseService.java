package com.restaurant.service;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.config.ErrorLogger;
import com.restaurant.dao.*;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Commande;
import com.restaurant.model.Paiement;
import com.restaurant.model.enums.StatutCommande;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CaisseService {

    private EntityManager entityManager;
    private CommandeDAO commandeDAO;
    private PaiementDAO paiementDAO;
    private PlatDAO platDAO;
    private CategorieDAO categorieDAO;
    private TableDAO tableDAO;

    private CommandeService commandeService;

    public CaisseService() {
        this.entityManager = DatabaseConfig.getEntityManager();
        this.commandeDAO = new CommandeDAO(entityManager);
        this.paiementDAO = new PaiementDAO(entityManager);
        this.platDAO = new PlatDAO(entityManager);
        this.categorieDAO = new CategorieDAO(entityManager);
        this.tableDAO = new TableDAO(entityManager);
        this.commandeService = new CommandeService();
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
     * Enregistre un paiement pour une commande
     */
    public Paiement recordPayment(Commande commande, Double montant, String modePaiement)
            throws ValidationException, DatabaseException {

        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        if (montant == null || montant <= 0) {
            throw new ValidationException("Montant doit être > 0");
        }

        if (modePaiement == null || modePaiement.trim().isEmpty()) {
            throw new ValidationException("Mode de paiement requis");
        }

        // Vérifier statut commande (doit être SERVI)
        if (!commande.getStatut().equals(StatutCommande.SERVI)) {
            throw new ValidationException(
                    "Paiement impossible, commande statut: " + commande.getStatut()
            );
        }

        // Calculer le total à payer
        Double totalAPayer = commandeService.calculateTotalAvecRemise(commande);

        if (montant < totalAPayer) {
            throw new ValidationException(
                    String.format("Montant insuffisant. Dû: %.2f€, Reçu: %.2f€", totalAPayer, montant)
            );
        }

        return executeWithTransaction("CaisseService.recordPayment", () -> {
            // Créer le paiement
            Paiement paiement = new Paiement();
            paiement.setCommande(commande);
            paiement.setMontant(montant);
            paiement.setModePaiement(modePaiement.trim());
            paiement.setDatePaiement(LocalDateTime.now());

            // Sauvegarder paiement
            Paiement savedPaiement = paiementDAO.save(paiement);

            // Mettre à jour statut commande
            commande.setStatut(StatutCommande.PAYEE);
            commandeDAO.save(commande);

            return savedPaiement;
        });
    }

    /**
     * Récupère le rendu de monnaie
     */
    public Double calculateRendu(Commande commande, Double montantPaye) throws ValidationException {
        if (commande == null) {
            throw new ValidationException("Commande requise");
        }

        Double totalAPayer = commandeService.calculateTotalAvecRemise(commande);

        if (montantPaye < totalAPayer) {
            throw new ValidationException("Montant insuffisant");
        }

        return montantPaye - totalAPayer;
    }

    /**
     * Récupère toutes les commandes servies (en attente de paiement)
     */
    public List<Commande> getCommandesAPayer() throws DatabaseException {
        return commandeDAO.findByStatut(StatutCommande.SERVI);
    }

    /**
     * Récupère toutes les commandes payées (pour rapports)
     */
    public List<Commande> getCommandesPayees() throws DatabaseException {
        return commandeDAO.findByStatut(StatutCommande.PAYEE);
    }

    /**
     * Récupère toutes les commandes payées aujourd'hui
     */
    public List<Commande> getCommandesPayeesAujourdhui() throws DatabaseException {
        LocalDate aujourdhui = LocalDate.now();

        return commandeDAO.findByStatut(StatutCommande.PAYEE).stream()
                .filter(c -> c.getDateCreation() != null)
                .filter(c -> c.getDateCreation().toLocalDate().equals(aujourdhui))
                .collect(Collectors.toList());
    }

    /**
     * Rapport : Total des ventes du jour
     */
    public Double getTotalVentesAujourdhui() throws DatabaseException {
        return getCommandesPayeesAujourdhui().stream()
                .mapToDouble(commandeService::calculateTotalAvecRemise)
                .sum();
    }

    /**
     * Rapport : Total des ventes par mode de paiement
     */
    public Map<String, Double> getVentesParModePaiement(LocalDate date) throws DatabaseException {
        List<Commande> commandesDuJour = getAllCommandesDuJour(date);

        return commandesDuJour.stream()
                .filter(c -> c.getStatut().equals(StatutCommande.PAYEE))
                .flatMap(c -> c.getPaiements().stream())
                .collect(Collectors.groupingBy(
                        Paiement::getModePaiement,
                        Collectors.summingDouble(Paiement::getMontant)
                ));
    }

    /**
     * Top 5 plats les plus vendus (toutes dates)
     */
    public List<PlatVente> getTop5Plats() throws DatabaseException {
        List<Commande> toutesCommandes = commandeDAO.findAll();

        return toutesCommandes.stream()
                .filter(c -> c.getStatut().equals(StatutCommande.PAYEE))
                .flatMap(c -> c.getLignes().stream())
                .collect(Collectors.groupingBy(
                        ligne -> ligne.getPlat().getNom(),
                        Collectors.summingInt(ligne -> ligne.getQuantite())
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new PlatVente(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Top 5 plats les plus vendus aujourd'hui
     */
    public List<PlatVente> getTop5PlatsAujourdhui() throws DatabaseException {
        List<Commande> commandesAujourdhui = getCommandesPayeesAujourdhui();

        return commandesAujourdhui.stream()
                .flatMap(c -> c.getLignes().stream())
                .collect(Collectors.groupingBy(
                        ligne -> ligne.getPlat().getNom(),
                        Collectors.summingInt(ligne -> ligne.getQuantite())
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new PlatVente(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Chiffre d'affaires par heure (aujourd'hui)
     */
    public Map<Integer, Double> getCAByHour() throws DatabaseException {
        List<Commande> commandesAujourdhui = getCommandesPayeesAujourdhui();

        return commandesAujourdhui.stream()
                .filter(c -> c.getDateCreation() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getDateCreation().getHour(),
                        Collectors.summingDouble(commandeService::calculateTotalAvecRemise)
                ));
    }

    /**
     * Statistiques de la journée
     */
    public StatsJournee getStatsJournee() throws DatabaseException {
        LocalDate aujourdhui = LocalDate.now();
        List<Commande> commandesDuJour = getAllCommandesDuJour(aujourdhui);

        StatsJournee stats = new StatsJournee();
        stats.setDate(aujourdhui);

        // Commandes par statut
        stats.setTotalCommandes(commandesDuJour.size());
        stats.setCommandesPayees(
                (int) commandesDuJour.stream()
                        .filter(c -> c.getStatut().equals(StatutCommande.PAYEE))
                        .count()
        );
        stats.setCommandesEnCours(
                (int) commandesDuJour.stream()
                        .filter(c -> !c.getStatut().equals(StatutCommande.PAYEE) &&
                                !c.getStatut().equals(StatutCommande.FINALISEE))
                        .count()
        );

        // Chiffre d'affaires
        stats.setChiffreAffaires(getTotalVentesAujourdhui());

        // Moyenne panier
        long nbCommandesPayees = commandesDuJour.stream()
                .filter(c -> c.getStatut().equals(StatutCommande.PAYEE))
                .count();

        if (nbCommandesPayees > 0) {
            stats.setMoyennePanier(stats.getChiffreAffaires() / nbCommandesPayees);
        }

        // Top plats
        stats.setTopPlats(getTop5PlatsAujourdhui());

        return stats;
    }

    // ==================== HELPER METHODS ====================

    private List<Commande> getAllCommandesDuJour(LocalDate date) throws DatabaseException {
        return commandeDAO.findAll().stream()
                .filter(c -> c.getDateCreation() != null)
                .filter(c -> c.getDateCreation().toLocalDate().equals(date))
                .collect(Collectors.toList());
    }

    // ==================== DTOs ====================

    /**
     * DTO pour les rapports de vente par plat
     */
    public static class PlatVente {
        private String nomPlat;
        private Integer quantiteVendue;

        public PlatVente(String nomPlat, Integer quantiteVendue) {
            this.nomPlat = nomPlat;
            this.quantiteVendue = quantiteVendue;
        }

        // Getters et Setters
        public String getNomPlat() { return nomPlat; }
        public void setNomPlat(String nomPlat) { this.nomPlat = nomPlat; }

        public Integer getQuantiteVendue() { return quantiteVendue; }
        public void setQuantiteVendue(Integer quantiteVendue) { this.quantiteVendue = quantiteVendue; }

        @Override
        public String toString() {
            return String.format("%s: %d unités", nomPlat, quantiteVendue);
        }
    }

    /**
     * DTO pour les statistiques de la journée
     */
    public static class StatsJournee {
        private LocalDate date;
        private int totalCommandes;
        private int commandesPayees;
        private int commandesEnCours;
        private double chiffreAffaires;
        private double moyennePanier;
        private List<PlatVente> topPlats;

        // Getters et Setters
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public int getTotalCommandes() { return totalCommandes; }
        public void setTotalCommandes(int totalCommandes) { this.totalCommandes = totalCommandes; }

        public int getCommandesPayees() { return commandesPayees; }
        public void setCommandesPayees(int commandesPayees) { this.commandesPayees = commandesPayees; }

        public int getCommandesEnCours() { return commandesEnCours; }
        public void setCommandesEnCours(int commandesEnCours) { this.commandesEnCours = commandesEnCours; }

        public double getChiffreAffaires() { return chiffreAffaires; }
        public void setChiffreAffaires(double chiffreAffaires) { this.chiffreAffaires = chiffreAffaires; }

        public double getMoyennePanier() { return moyennePanier; }
        public void setMoyennePanier(double moyennePanier) { this.moyennePanier = moyennePanier; }

        public List<PlatVente> getTopPlats() { return topPlats; }
        public void setTopPlats(List<PlatVente> topPlats) { this.topPlats = topPlats; }

        @Override
        public String toString() {
            return String.format(
                    "Stats Journée %s: %d commandes (payées: %d, en cours: %d), %.2f€ CA, %.2f€ panier moyen",
                    date, totalCommandes, commandesPayees, commandesEnCours, chiffreAffaires, moyennePanier
            );
        }
    }
}