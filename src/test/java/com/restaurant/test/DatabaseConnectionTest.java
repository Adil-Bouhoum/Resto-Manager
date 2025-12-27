package com.restaurant.test;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.dao.CategorieDAO;
import com.restaurant.dao.PlatDAO;
import com.restaurant.dao.TableDAO;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Categorie;

import javax.persistence.EntityManager;

/**
 * Test simple pour vérifier :
 * 1. Connexion H2
 * 2. Création des tables
 * 3. CRUD basique
 * 4. Données initiales
 */
public class DatabaseConnectionTest {

    public static void main(String[] args) {
        System.out.println("\n========== TEST CONNECTION BASE DE DONNÉES ==========\n");

        EntityManager em = null;
        try {
            // 1️⃣ Test connexion
            System.out.println("1️⃣ Test connexion...");
            em = DatabaseConfig.getEntityManager();
            System.out.println("✅ Connexion réussie\n");

            // 2️⃣ Test lecture catégories
            System.out.println("2️⃣ Récupération des catégories...");
            CategorieDAO catDAO = new CategorieDAO(em);
            var categories = catDAO.findAll();
            System.out.println("✅ " + categories.size() + " catégories trouvées:");
            categories.forEach(c -> System.out.println("   - " + c.getNom()));
            System.out.println();

            // 3️⃣ Test lecture plats
            System.out.println("3️⃣ Récupération des plats...");
            PlatDAO platDAO = new PlatDAO(em);
            var plats = platDAO.findAll();
            System.out.println("✅ " + plats.size() + " plats trouvés:");
            plats.forEach(p -> System.out.println("   - " + p.getNom() + " (" + p.getPrix() + "€)"));
            System.out.println();

            // 4️⃣ Test lecture tables
            System.out.println("4️⃣ Récupération des tables...");
            TableDAO tableDAO = new TableDAO(em);
            var tables = tableDAO.findAll();
            System.out.println("✅ " + tables.size() + " tables trouvées:");
            tables.forEach(t -> System.out.println("   - Table " + t.getNumeroTable() + " (capacité: " + t.getCapacite() + ")"));
            System.out.println();

            // 5️⃣ Test insertion (CREATE)
            System.out.println("5️⃣ Test insertion d'une nouvelle catégorie...");
            Categorie newCat = new Categorie();
            newCat.setNom("Test Catégorie");
            newCat.setDescription("Catégorie créée par test");
            Categorie savedCat = catDAO.save(newCat);
            System.out.println("✅ Catégorie sauvegardée avec ID: " + savedCat.getId() + "\n");

            // 6️⃣ Test lecture (READ)
            System.out.println("6️⃣ Test lecture de la catégorie...");
            Categorie readCat = catDAO.findById(savedCat.getId());
            System.out.println("✅ Catégorie lue: " + readCat.getNom() + "\n");

            // 7️⃣ Test modification (UPDATE)
            System.out.println("7️⃣ Test modification de la catégorie...");
            readCat.setDescription("Description modifiée");
            Categorie updatedCat = catDAO.save(readCat);
            System.out.println("✅ Catégorie modifiée\n");

            // 8️⃣ Test suppression (DELETE)
            System.out.println("8️⃣ Test suppression de la catégorie...");
            catDAO.delete(updatedCat.getId());
            System.out.println("✅ Catégorie supprimée\n");

            System.out.println("========== ✅ TOUS LES TESTS RÉUSSIS ==========\n");

        } catch (ValidationException e) {
            System.err.println("❌ Erreur validation: " + e.getMessage());
        } catch (DatabaseException e) {
            System.err.println("❌ Erreur base de données: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Erreur inattendue: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Fermer EntityManager
            if (em != null && em.isOpen()) {
                em.close();
            }
            // Fermer la factory (une seule fois)
            DatabaseConfig.shutdown();
        }
    }
}