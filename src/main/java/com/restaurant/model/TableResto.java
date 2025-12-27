package com.restaurant.model;

import com.restaurant.model.enums.StatutCommande;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "table_resto")
public class TableResto extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Integer numeroTable;

    @Column(nullable = false)
    private Integer capacite;

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Commande> commandes = new ArrayList<>();

    // Constructeurs
    public TableResto() {
    }

    public TableResto(Integer numeroTable, Integer capacite) {
        this.numeroTable = numeroTable;
        this.capacite = capacite;
    }

    // Getters et Setters
    public Integer getNumeroTable() {
        return numeroTable;
    }

    public void setNumeroTable(Integer numeroTable) {
        this.numeroTable = numeroTable;
    }

    public Integer getCapacite() {
        return capacite;
    }

    public void setCapacite(Integer capacite) {
        this.capacite = capacite;
    }

    public List<Commande> getCommandes() {
        return commandes;
    }

    public void setCommandes(List<Commande> commandes) {
        this.commandes = commandes;
    }

    // Méthode calculée pour savoir si la table est occupée
    @Transient
    public boolean isOccupee() {
        try {
            if (commandes == null || commandes.isEmpty()) {
                return false;
            }

            // DEBUG: Afficher toutes les commandes
            System.out.println("[DEBUG] Table " + numeroTable + " a " + commandes.size() + " commande(s)");

            for (Commande commande : commandes) {
                System.out.println("[DEBUG]   Commande #" + commande.getId() +
                        " - Statut: " + commande.getStatut());
            }

            // Statuts qui indiquent une table libre
            boolean occupee = commandes.stream()
                    .anyMatch(commande -> {
                        if (commande == null || commande.getStatut() == null) {
                            return false;
                        }
                        StatutCommande statut = commande.getStatut();
                        return statut != StatutCommande.FINALISEE &&
                                statut != StatutCommande.ANNULEE &&
                                statut != StatutCommande.PAYEE;
                    });

            System.out.println("[DEBUG] Table " + numeroTable + " - isOccupee(): " + occupee);
            return occupee;

        } catch (Exception e) {
            System.out.println("[ERROR] isOccupee() erreur: " + e.getMessage());
            return false;
        }
    }

    public void addCommande(Commande commande) {
        commandes.add(commande);
        commande.setTable(this);
    }

    public void removeCommande(Commande commande) {
        commandes.remove(commande);
        commande.setTable(null);
    }

    @Override
    public String toString() {
        return "Table " + numeroTable + " (" + capacite + " places)";
    }
}