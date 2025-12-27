package com.restaurant.model;

import javax.persistence.*;

@Entity
@Table(name = "ligne_commande")
public class LigneCommande extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plat_id", nullable = false)
    private Plat plat;

    @Column(nullable = false)
    private Integer quantite;

    @Column(nullable = false)
    private Double prix;

    // Constructeurs
    public LigneCommande() {
    }

    public LigneCommande(Commande commande, Plat plat, Integer quantite) {
        this.commande = commande;
        this.plat = plat;
        this.quantite = quantite;
        this.prix = plat.getPrix(); // Capture le prix actuel du plat
    }

    // Getters et Setters
    public Commande getCommande() {
        return commande;
    }

    public void setCommande(Commande commande) {
        this.commande = commande;
    }

    public Plat getPlat() {
        return plat;
    }

    public void setPlat(Plat plat) {
        this.plat = plat;
    }

    public Integer getQuantite() {
        return quantite;
    }

    public void setQuantite(Integer quantite) {
        this.quantite = quantite;
    }

    public Double getPrix() {
        return prix;
    }

    public void setPrix(Double prix) {
        this.prix = prix;
    }

    public Double getSousTotal() {
        return prix * quantite;
    }

    @Override
    public String toString() {
        return quantite + "x " + (plat != null ? plat.getNom() : "?") + " - " + getSousTotal() + "€";
    }
}