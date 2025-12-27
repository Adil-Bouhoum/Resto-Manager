package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Categorie;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

public class CategorieDAO extends GenericDAO<Categorie> {

    public CategorieDAO(EntityManager entityManager) {
        super(entityManager, Categorie.class);
    }

    public Categorie findByNom(String nom) throws DatabaseException {
        try {
            String query = "FROM Categorie c WHERE c.nom = :nom";
            TypedQuery<Categorie> q = entityManager.createQuery(query, Categorie.class);
            q.setParameter("nom", nom);
            try {
                return q.getSingleResult();
            } catch (javax.persistence.NoResultException e) {
                return null;
            }
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche catégorie par nom", e);
        }
    }

    @Override
    protected void validateEntity(Categorie entity) throws ValidationException {
        if (entity.getNom() == null || entity.getNom().trim().isEmpty()) {
            throw new ValidationException("Le nom de la catégorie est requis");
        }
        if (entity.getNom().length() > 100) {
            throw new ValidationException("Le nom ne doit pas dépasser 100 caractères");
        }
    }
}
