package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Categorie;
import com.restaurant.model.Plat;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class PlatDAO extends GenericDAO<Plat> {

    public PlatDAO(EntityManager entityManager) {
        super(entityManager, Plat.class);
    }

    public List<Plat> findByCategorie(Categorie categorie) throws DatabaseException {
        try {
            String query = "FROM Plat p WHERE p.categorie = :cat ORDER BY p.nom";
            TypedQuery<Plat> q = entityManager.createQuery(query, Plat.class);
            q.setParameter("cat", categorie);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche plats par catégorie", e);
        }
    }

    /**
     * Recherche simple de plats par nom (recherche insensible à la casse)
     */
    public List<Plat> searchByName(String nomTerm) throws DatabaseException {
        try {
            String query = "FROM Plat p WHERE LOWER(p.nom) LIKE LOWER(:term) ORDER BY p.nom";
            TypedQuery<Plat> q = entityManager.createQuery(query, Plat.class);
            q.setParameter("term", "%" + nomTerm + "%");
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche plats par nom: " + nomTerm, e);
        }
    }

    /**
     * Trouve les plats les plus populaires (ceux avec le plus de commandes)
     * Simple : compte le nombre de lignes de commande pour chaque plat
     */
    public List<Plat> findMostPopular(int limit) throws DatabaseException {
        try {
            String query = "SELECT p FROM Plat p " +
                    "LEFT JOIN p.lignesCommande lc " +
                    "GROUP BY p " +
                    "ORDER BY COUNT(lc) DESC, p.nom";

            TypedQuery<Plat> q = entityManager.createQuery(query, Plat.class);
            q.setMaxResults(limit);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche plats populaires", e);
        }
    }

    /**
     * Variante encore plus simple : plats avec au moins une commande
     */
    public List<Plat> findOrderedPlats(int limit) throws DatabaseException {
        try {
            String query = "SELECT DISTINCT p FROM Plat p " +
                    "JOIN p.lignesCommande lc " +
                    "ORDER BY p.nom";

            TypedQuery<Plat> q = entityManager.createQuery(query, Plat.class);
            q.setMaxResults(limit);
            return q.getResultList();
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche plats commandés", e);
        }
    }

    @Override
    protected void validateEntity(Plat entity) throws ValidationException {
        if (entity.getNom() == null || entity.getNom().trim().isEmpty()) {
            throw new ValidationException("Le nom du plat est requis");
        }
        if (entity.getPrix() == null || entity.getPrix() <= 0) {
            throw new ValidationException("Le prix doit être > 0");
        }
        if (entity.getCategorie() == null) {
            throw new ValidationException("Une catégorie doit être sélectionnée");
        }
    }
}