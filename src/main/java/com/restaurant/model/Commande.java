package com.restaurant.model;

import com.restaurant.model.enums.StatutCommande;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commande")
public class Commande extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private TableResto table;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCommande statut = StatutCommande.EN_ATTENTE;

    @Column
    private Double remiseAppliquee = 0.0;

    @Column
    private LocalDateTime dateCommande; // Date/heure de prise de commande

    @Column
    private LocalDateTime dateServi; // Date/heure de service

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommande> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Paiement> paiements = new ArrayList<>();

    // Constructeurs
    public Commande() {
        this.dateCommande = LocalDateTime.now();
    }

    public Commande(TableResto table) {
        this.table = table;
        this.statut = StatutCommande.EN_ATTENTE;
        this.dateCommande = LocalDateTime.now();
    }

    // Getters et Setters
    public TableResto getTable() {
        return table;
    }

    public void setTable(TableResto table) {
        this.table = table;
    }

    public StatutCommande getStatut() {
        return statut;
    }

    public void setStatut(StatutCommande statut) {
        this.statut = statut;

        // Mettre à jour dateServi quand la commande est servie
        if (statut == StatutCommande.SERVI && this.dateServi == null) {
            this.dateServi = LocalDateTime.now();
        }
    }

    public Double getRemiseAppliquee() {
        return remiseAppliquee != null ? remiseAppliquee : 0.0;
    }

    public void setRemiseAppliquee(Double remiseAppliquee) {
        this.remiseAppliquee = remiseAppliquee;
    }

    public LocalDateTime getDateCommande() {
        return dateCommande;
    }

    public void setDateCommande(LocalDateTime dateCommande) {
        this.dateCommande = dateCommande;
    }

    public LocalDateTime getDateServi() {
        return dateServi;
    }

    public void setDateServi(LocalDateTime dateServi) {
        this.dateServi = dateServi;
    }

    public List<LigneCommande> getLignes() {
        return lignes;
    }

    public void setLignes(List<LigneCommande> lignes) {
        this.lignes = lignes;
    }

    public List<Paiement> getPaiements() {
        return paiements;
    }

    public void setPaiements(List<Paiement> paiements) {
        this.paiements = paiements;
    }

    // Méthodes de calcul
    public Double getTotal() {
        if (lignes == null || lignes.isEmpty()) {
            return 0.0;
        }

        return lignes.stream()
                .mapToDouble(LigneCommande::getSousTotal)
                .sum();
    }

    public Double getTotalAvecRemise() {
        double total = getTotal();
        double remise = getRemiseAppliquee();
        return Math.max(0, total - remise);
    }

    public void addLigne(LigneCommande ligne) {
        lignes.add(ligne);
        ligne.setCommande(this);
    }

    public void removeLigne(LigneCommande ligne) {
        lignes.remove(ligne);
        ligne.setCommande(null);
    }

    public void addPaiement(Paiement paiement) {
        paiements.add(paiement);
        paiement.setCommande(this);
    }

    public void removePaiement(Paiement paiement) {
        paiements.remove(paiement);
        paiement.setCommande(null);
    }

    @Override
    public String toString() {
        return "Commande #" + getId() + " - Table " + (table != null ? table.getNumeroTable() : "?")
                + " - " + statut + " - " + getTotalAvecRemise() + "€";
    }
}