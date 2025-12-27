package com.restaurant.model.enums;

public enum StatutCommande {
    EN_ATTENTE("En attente"),
    EN_PREPARATION("En préparation"),
    PRET("Prêt"),
    SERVI("Servi"),
    PAYEE("Payée"),
    FINALISEE("Finalisée"),
    ANNULEE("Annulée");

    private final String libelle;

    StatutCommande(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    // Méthodes utilitaires
    public boolean estModifiable() {
        return this == EN_ATTENTE;
    }

    public boolean estEnCours() {
        return this == EN_ATTENTE || this == EN_PREPARATION || this == PRET;
    }

    public boolean estServie() {
        return this == SERVI;
    }

    public boolean estPayee() {
        return this == PAYEE;
    }

    public boolean estFinalisee() {
        return this == FINALISEE;
    }

    public boolean peutEtreAnnulee() {
        return this == EN_ATTENTE || this == EN_PREPARATION;
    }

    @Override
    public String toString() {
        return libelle;
    }
}