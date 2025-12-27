package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Commande;
import com.restaurant.model.Paiement;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class PaiementDAO extends GenericDAO<Paiement> {

    public PaiementDAO(EntityManager entityManager) {
        super(entityManager, Paiement.class);
    }

    public List<Paiement> findByCommande(Commande commande) throws DatabaseException {
        try {
            String query = "FROM Paiement p WHERE p.commande = :cmd ORDER BY p.dateCreation";
            TypedQuery<Paiement> q = entityManager.createQuery(query, Paiement.class);
            q.setParameter("cmd", commande);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche paiements", e);
        }
    }

    @Override
    protected void validateEntity(Paiement entity) throws ValidationException {
        if (entity.getMontant() == null || entity.getMontant() <= 0) {
            throw new ValidationException("Le montant du paiement doit être > 0");
        }
        if (entity.getModePaiement() == null || entity.getModePaiement().trim().isEmpty()) {
            throw new ValidationException("Le mode de paiement est requis");
        }
        if (entity.getCommande() == null) {
            throw new ValidationException("Une commande doit être associée au paiement");
        }
    }
}