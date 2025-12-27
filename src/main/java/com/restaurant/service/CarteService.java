package com.restaurant.service;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.config.ErrorLogger;
import com.restaurant.config.ImageManager;
import com.restaurant.dao.CategorieDAO;
import com.restaurant.dao.PlatDAO;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Categorie;
import com.restaurant.model.Plat;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.File;
import java.util.List;

/**
 * Service métier pour gestion du menu (catégories + plats)
 * Orchestration entre Controller et DAO
 */
public class CarteService {

    private EntityManager entityManager;
    private CategorieDAO categorieDAO;
    private PlatDAO platDAO;

    public CarteService() {
        this.entityManager = DatabaseConfig.getEntityManager();
        this.categorieDAO = new CategorieDAO(entityManager);
        this.platDAO = new PlatDAO(entityManager);
    }

    /**
     * Méthode utilitaire pour exécuter une opération dans une transaction
     */
    private <T> T executeWithTransaction(String context, TransactionCallback<T> callback)
            throws DatabaseException, ValidationException {
        EntityTransaction transaction = null;
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();

            T result = callback.execute();

            transaction.commit();
            return result;

        } catch (ValidationException ve) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw ve;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            ErrorLogger.logError(context, e);
            throw new DatabaseException("Erreur lors de l'opération: " + context, e);
        }
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {
        T execute() throws Exception;
    }

    // ==================== CATÉGORIES ====================

    /**
     * Récupère toutes les catégories
     */
    public List<Categorie> getAllCategories() throws DatabaseException {
        return categorieDAO.findAll();
    }

    /**
     * Ajoute une catégorie (validation unique)
     */
    public Categorie addCategorie(String nom, String description)
            throws ValidationException, DatabaseException {

        // Validation
        if (nom == null || nom.trim().isEmpty()) {
            throw new ValidationException("Le nom de la catégorie est requis");
        }

        String nomTrim = nom.trim();

        // Vérifier unicité du nom
        Categorie existing = categorieDAO.findByNom(nomTrim);
        if (existing != null) {
            throw new ValidationException("La catégorie '" + nomTrim + "' existe déjà");
        }

        return executeWithTransaction("CarteService.addCategorie", () -> {
            Categorie categorie = new Categorie(nomTrim, description);
            return categorieDAO.save(categorie);
        });
    }

    /**
     * Met à jour une catégorie
     */
    public Categorie updateCategorie(Long id, String nom, String description)
            throws ValidationException, DatabaseException {

        // Validation
        if (nom == null || nom.trim().isEmpty()) {
            throw new ValidationException("Le nom de la catégorie est requis");
        }

        String nomTrim = nom.trim();

        // Récupérer la catégorie
        Categorie categorie = categorieDAO.findById(id);
        if (categorie == null) {
            throw new ValidationException("Catégorie introuvable (ID: " + id + ")");
        }

        // Vérifier unicité (sauf elle-même)
        if (!categorie.getNom().equals(nomTrim)) {
            Categorie existing = categorieDAO.findByNom(nomTrim);
            if (existing != null && !existing.getId().equals(id)) {
                throw new ValidationException("La catégorie '" + nomTrim + "' existe déjà");
            }
        }

        return executeWithTransaction("CarteService.updateCategorie", () -> {
            categorie.setNom(nomTrim);
            categorie.setDescription(description);
            return categorieDAO.save(categorie);
        });
    }

    /**
     * Supprime une catégorie (si pas de plats)
     */
    public void deleteCategorie(Long id) throws ValidationException, DatabaseException {
        Categorie categorie = categorieDAO.findById(id);
        if (categorie == null) {
            throw new ValidationException("Catégorie introuvable (ID: " + id + ")");
        }

        // Vérifier si la catégorie contient des plats
        if (!categorie.getPlats().isEmpty()) {
            throw new ValidationException(
                    "Impossible de supprimer: la catégorie contient " +
                            categorie.getPlats().size() + " plat(s)"
            );
        }

        executeWithTransaction("CarteService.deleteCategorie", () -> {
            categorieDAO.delete(id);
            return null;
        });
    }

    // ==================== PLATS ====================

    /**
     * Récupère tous les plats
     */
    public List<Plat> getAllPlats() throws DatabaseException {
        return platDAO.findAll();
    }

    /**
     * Récupère plats par catégorie
     */
    public List<Plat> getPlatsByCategorie(Categorie categorie) throws DatabaseException, ValidationException {
        if (categorie == null) {
            throw new ValidationException("Catégorie requise");
        }
        return platDAO.findByCategorie(categorie);
    }

    /**
     * Récupère plats par ID de catégorie
     */
    public List<Plat> getPlatsByCategorieId(Long categorieId) throws DatabaseException, ValidationException {
        Categorie categorie = categorieDAO.findById(categorieId);
        if (categorie == null) {
            throw new ValidationException("Catégorie introuvable (ID: " + categorieId + ")");
        }
        return platDAO.findByCategorie(categorie);
    }

    /**
     * Ajoute un plat (version simplifiée sans image)
     */
    public Plat addPlat(String nom, Double prix, Categorie categorie, String description)
            throws ValidationException, DatabaseException {

        // Validation
        if (nom == null || nom.trim().isEmpty()) {
            throw new ValidationException("Le nom du plat est requis");
        }

        if (prix == null || prix <= 0) {
            throw new ValidationException("Le prix doit être supérieur à 0");
        }

        if (categorie == null) {
            throw new ValidationException("Une catégorie est requise");
        }

        String nomTrim = nom.trim();

        return executeWithTransaction("CarteService.addPlat", () -> {
            Plat plat = new Plat();
            plat.setNom(nomTrim);
            plat.setPrix(prix);
            plat.setCategorie(categorie);
            plat.setDescription(description);

            return platDAO.save(plat);
        });
    }

    /**
     * Ajoute un plat avec image (fichier)
     */
    public Plat addPlatWithImage(String nom, Double prix, Categorie categorie,
                                 String description, File imageFile)
            throws ValidationException, DatabaseException {

        // Validation de base
        if (nom == null || nom.trim().isEmpty()) {
            throw new ValidationException("Le nom du plat est requis");
        }

        if (prix == null || prix <= 0) {
            throw new ValidationException("Le prix doit être supérieur à 0");
        }

        if (categorie == null) {
            throw new ValidationException("Une catégorie est requise");
        }

        String nomTrim = nom.trim();
        String imageBase64 = null;
        String imagePath = null;

        // Gestion de l'image
        if (imageFile != null && imageFile.exists()) {
            try {
                // Convertir l'image en Base64
                imageBase64 = ImageManager.imageToBase64(imageFile);

                // Sauvegarder aussi en fichier
                String fileName = System.currentTimeMillis() + "_" + imageFile.getName();
                imagePath = ImageManager.saveImageFile(imageBase64, fileName);
            } catch (Exception e) {
                ErrorLogger.logError("CarteService.addPlatWithImage - conversion image", e);
                // Continuer sans image si échec de conversion
            }
        }

        String finalImageBase6 = imageBase64;
        String finalImagePath = imagePath;
        return executeWithTransaction("CarteService.addPlatWithImage", () -> {
            Plat plat = new Plat();
            plat.setNom(nomTrim);
            plat.setPrix(prix);
            plat.setCategorie(categorie);
            plat.setDescription(description);
            plat.setImageBase64(finalImageBase6);
            plat.setImagePath(finalImagePath);

            return platDAO.save(plat);
        });
    }

    /**
     * Met à jour un plat
     */
    public Plat updatePlat(Long id, String nom, Double prix, Categorie categorie,
                           String description, File imageFile)
            throws ValidationException, DatabaseException {

        // Validation
        if (nom == null || nom.trim().isEmpty()) {
            throw new ValidationException("Le nom du plat est requis");
        }

        if (prix == null || prix <= 0) {
            throw new ValidationException("Le prix doit être supérieur à 0");
        }

        if (categorie == null) {
            throw new ValidationException("Une catégorie est requise");
        }

        // Récupérer le plat
        Plat plat = platDAO.findById(id);
        if (plat == null) {
            throw new ValidationException("Plat introuvable (ID: " + id + ")");
        }

        String nomTrim = nom.trim();
        String imageBase64 = plat.getImageBase64();
        String imagePath = plat.getImagePath();

        // Gestion de l'image
        if (imageFile != null && imageFile.exists()) {
            try {
                // Supprimer l'ancienne image fichier si elle existe
                if (imagePath != null && !imagePath.isEmpty()) {
                    ImageManager.deleteImageFile(imagePath);
                }

                // Convertir la nouvelle image
                imageBase64 = ImageManager.imageToBase64(imageFile);

                // Sauvegarder en fichier
                String fileName = System.currentTimeMillis() + "_" + imageFile.getName();
                imagePath = ImageManager.saveImageFile(imageBase64, fileName);
            } catch (Exception e) {
                ErrorLogger.logError("CarteService.updatePlat - conversion image", e);
                // Garder l'ancienne image si échec
            }
        }

        String finalImageBase6 = imageBase64;
        String finalImagePath = imagePath;
        return executeWithTransaction("CarteService.updatePlat", () -> {
            plat.setNom(nomTrim);
            plat.setPrix(prix);
            plat.setCategorie(categorie);
            plat.setDescription(description);
            plat.setImageBase64(finalImageBase6);
            plat.setImagePath(finalImagePath);

            return platDAO.save(plat);
        });
    }

    /**
     * Supprime un plat (si pas dans commandes)
     */
    public void deletePlat(Long id) throws ValidationException, DatabaseException {
        Plat plat = platDAO.findById(id);
        if (plat == null) {
            throw new ValidationException("Plat introuvable (ID: " + id + ")");
        }

        // Vérifier si le plat est utilisé dans des commandes
        if (!plat.getLignesCommande().isEmpty()) {
            throw new ValidationException(
                    "Impossible de supprimer: le plat est présent dans " +
                            plat.getLignesCommande().size() + " commande(s)"
            );
        }

        executeWithTransaction("CarteService.deletePlat", () -> {
            // Supprimer l'image fichier si elle existe
            if (plat.getImagePath() != null && !plat.getImagePath().isEmpty()) {
                ImageManager.deleteImageFile(plat.getImagePath());
            }

            platDAO.delete(id);
            return null;
        });
    }

    /**
     * Supprime uniquement l'image d'un plat
     */
    public void removePlatImage(Long id) throws ValidationException, DatabaseException {
        Plat plat = platDAO.findById(id);
        if (plat == null) {
            throw new ValidationException("Plat introuvable (ID: " + id + ")");
        }

        executeWithTransaction("CarteService.removePlatImage", () -> {
            // Supprimer le fichier image
            if (plat.getImagePath() != null && !plat.getImagePath().isEmpty()) {
                ImageManager.deleteImageFile(plat.getImagePath());
            }

            // Effacer les références à l'image
            plat.setImageBase64(null);
            plat.setImagePath(null);

            platDAO.save(plat);
            return null;
        });
    }

    /**
     * Obtient un plat par ID
     */
    public Plat getPlatById(Long id) throws DatabaseException {
        return platDAO.findById(id);
    }

    /**
     * Obtient une catégorie par ID
     */
    public Categorie getCategorieById(Long id) throws DatabaseException {
        return categorieDAO.findById(id);
    }

    /**
     * Recherche des plats par nom (recherche partielle)
     */
    public List<Plat> searchPlatsByName(String searchTerm) throws DatabaseException {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAllPlats();
        }

        return platDAO.searchByName(searchTerm.trim());
    }

    /**
     * Récupère les plats les plus populaires (par nombre de commandes)
     */
    public List<Plat> getPopularPlats(int limit) throws DatabaseException {
        return platDAO.findMostPopular(limit);
    }

    /**
     * Compte le nombre de plats par catégorie
     */
    /** public long countPlatsByCategorie(Categorie categorie) throws DatabaseException {
    } */

    /**
     * Vérifie si une catégorie existe par nom
     */
    public boolean categorieExists(String nom) throws DatabaseException {
        if (nom == null || nom.trim().isEmpty()) {
            return false;
        }

        return categorieDAO.findByNom(nom.trim()) != null;
    }

    /**
     * Ferme les ressources
     */
    public void cleanup() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
    }
}