package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.TableResto;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

public class TableDAO extends GenericDAO<TableResto> {

    public TableDAO(EntityManager entityManager) {
        super(entityManager, TableResto.class);
    }

    public TableResto findByNumero(Integer numero) throws DatabaseException {
        try {
            String query = "FROM TableResto t WHERE t.numeroTable = :num";
            TypedQuery<TableResto> q = entityManager.createQuery(query, TableResto.class);
            q.setParameter("num", numero);
            try {
                return q.getSingleResult();
            } catch (javax.persistence.NoResultException e) {
                return null;
            }
        } catch (Exception e) {
            throw new DatabaseException("Erreur recherche table par numéro", e);
        }
    }

    @Override
    protected void validateEntity(TableResto entity) throws ValidationException {
        if (entity.getNumeroTable() == null || entity.getNumeroTable() <= 0) {
            throw new ValidationException("Le numéro de table doit être > 0");
        }
        if (entity.getCapacite() == null || entity.getCapacite() <= 0) {
            throw new ValidationException("La capacité doit être > 0");
        }
    }
}