package com.restaurant.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiement")
public class Paiement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Column(nullable = false)
    private Double montant;

    @Column(nullable = false, length = 50)
    private String modePaiement; // "ESPECES", "CARTE", "TICKET_RESTO"

    @Column(length = 100)
    private String reference; // Numéro de transaction carte, etc.

    @Column(nullable = false)
    private LocalDateTime datePaiement;

    // Constructeurs
    public Paiement() {
        this.datePaiement = LocalDateTime.now();
    }

    public Paiement(Commande commande, Double montant, String modePaiement) {
        this.commande = commande;
        this.montant = montant;
        this.modePaiement = modePaiement;
        this.datePaiement = LocalDateTime.now();
    }

    // Getters et Setters
    public Commande getCommande() {
        return commande;
    }

    public void setCommande(Commande commande) {
        this.commande = commande;
    }

    public Double getMontant() {
        return montant;
    }

    public void setMontant(Double montant) {
        this.montant = montant;
    }

    public String getModePaiement() {
        return modePaiement;
    }

    public void setModePaiement(String modePaiement) {
        this.modePaiement = modePaiement;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDateTime getDatePaiement() {
        return datePaiement;
    }

    public void setDatePaiement(LocalDateTime datePaiement) {
        this.datePaiement = datePaiement;
    }

    @Override
    public String toString() {
        return "Paiement: " + montant + "€ par " + modePaiement + " le " + datePaiement;
    }
}