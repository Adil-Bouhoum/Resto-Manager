package com.restaurant.dao;

import com.restaurant.config.ErrorLogger;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.BaseEntity;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.util.List;

public abstract class GenericDAO<T extends BaseEntity> {

    protected EntityManager entityManager;
    protected Class<T> entityClass;

    public GenericDAO(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    /**
     * Récupérer une entité par ID
     */
    public T findById(Long id) throws DatabaseException {
        try {
            return entityManager.find(entityClass, id);
        } catch (Exception e) {
            ErrorLogger.logError("DAO.findById - " + entityClass.getSimpleName() + " ID: " + id, e);
            throw new DatabaseException("Erreur recherche entité", e);
        }
    }

    /**
     * Récupérer toutes les entités
     */
    public List<T> findAll() throws DatabaseException {
        try {
            String query = "FROM " + entityClass.getSimpleName();
            return entityManager.createQuery(query, entityClass).getResultList();
        } catch (Exception e) {
            ErrorLogger.logError("DAO.findAll - " + entityClass.getSimpleName(), e);
            throw new DatabaseException("Erreur récupération liste", e);
        }
    }
    /**
     * Sauvegarder ou mettre à jour une entité
     */
    public T save(T entity) throws ValidationException, DatabaseException {
        try {
            validateEntity(entity);

            // PAS DE begin()/commit() ICI
            if (entity.getId() == null) {
                entityManager.persist(entity);
            } else {
                entity = entityManager.merge(entity);
            }
            return entity;
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            ErrorLogger.logError("DAO.save - " + entityClass.getSimpleName(), e);
            throw new DatabaseException("Erreur sauvegarde entité", e);
        }
    }

    /**
     * Supprimer une entité par ID
     */
    public void delete(Long id) throws DatabaseException {
        try {
            T entity = findById(id);
            if (entity != null) {
                // PAS DE begin()/commit() ICI
                entityManager.remove(entity);
            }
        } catch (Exception e) {
            ErrorLogger.logError("DAO.delete - " + entityClass.getSimpleName() + " ID: " + id, e);
            throw new DatabaseException("Erreur suppression entité", e);
        }
    }


    /**
     * Vérifier l'existence d'une entité
     */
    public boolean exists(Long id) throws DatabaseException {
        try {
            return findById(id) != null;
        } catch (DatabaseException e) {
            throw e;
        }
    }

    /**
     * Compter le nombre d'entités
     */
    public long count() throws DatabaseException {
        try {
            String query = "SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e";
            return entityManager.createQuery(query, Long.class).getSingleResult();
        } catch (Exception e) {
            ErrorLogger.logError("DAO.count - " + entityClass.getSimpleName(), e);
            throw new DatabaseException("Erreur comptage entités", e);
        }
    }

    /**
     * Valider l'entité avant sauvegarde (à implémenter dans les sous-classes)
     */
    protected abstract void validateEntity(T entity) throws ValidationException;

    /**
     * Rollback la transaction si active
     */
    private void rollbackIfActive(EntityTransaction tx) {
        try {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
        } catch (Exception e) {
            ErrorLogger.logError("DAO.rollback - Erreur during rollback", e);
        }
    }

    /**
     * Fermer l'EntityManager
     */
    public void close() {
        try {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
            }
        } catch (Exception e) {
            ErrorLogger.logError("DAO.close - Erreur fermeture EntityManager", e);
        }
    }
}