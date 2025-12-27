package com.restaurant.config;

import javafx.scene.image.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public class ImageManager {
    private static final String UPLOAD_DIR = "./uploads/";
    private static final String DEFAULT_IMAGE = "/images/plats/default.jpg";

    static {
        // Créer le dossier uploads s'il n'existe pas
        File uploadsDir = new File(UPLOAD_DIR);
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
    }

    /**
     * Charge l'image d'un plat (priorité: Base64 > fichier > défaut)
     */
    public static Image loadImage(Object plat) {
        try {
            // Si c'est un objet avec imageBase64
            String base64 = getBase64FromPlat(plat);
            if (base64 != null && !base64.isEmpty()) {
                return decodeBase64ToImage(base64);
            }

            // Sinon essayer le chemin fichier
            String imagePath = getImagePathFromPlat(plat);
            if (imagePath != null && !imagePath.isEmpty()) {
                File file = new File(UPLOAD_DIR + imagePath);
                if (file.exists()) {
                    return new Image(file.toURI().toString());
                }
            }

            // Fallback : image par défaut
            return new Image(DEFAULT_IMAGE);

        } catch (Exception e) {
            ErrorLogger.logError("Erreur chargement image plat", e);
            return new Image(DEFAULT_IMAGE);
        }
    }

    /**
     * Convertir un fichier image en Base64
     */
    public static String imageToBase64(File imageFile) throws IOException {
        if (!imageFile.exists()) {
            throw new IOException("Fichier image introuvable: " + imageFile.getAbsolutePath());
        }
        byte[] fileContent = Files.readAllBytes(imageFile.toPath());
        return Base64.getEncoder().encodeToString(fileContent);
    }

    /**
     * Décoder Base64 en Image JavaFX
     */
    public static Image decodeBase64ToImage(String base64String) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);
        return new Image(new java.io.ByteArrayInputStream(decodedBytes));
    }

    /**
     * Sauvegarder une image (Base64) dans un fichier physique
     */
    public static String saveImageFile(String base64String, String fileName) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);
        File uploadFile = new File(UPLOAD_DIR + fileName);
        Files.write(uploadFile.toPath(), decodedBytes);
        return fileName; // Retourner chemin relatif pour BD
    }

    /**
     * Supprimer un fichier image
     */
    public static boolean deleteImageFile(String imagePath) {
        try {
            if (imagePath == null || imagePath.isEmpty()) {
                return true;
            }
            File file = new File(UPLOAD_DIR + imagePath);
            if (file.exists()) {
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            ErrorLogger.logError("Erreur suppression image: " + imagePath, e);
            return false;
        }
    }

    // === HELPERS RÉFLEXION (pour éviter dépendance directe à Plat) ===

    private static String getBase64FromPlat(Object plat) {
        try {
            return (String) plat.getClass().getMethod("getImageBase64").invoke(plat);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getImagePathFromPlat(Object plat) {
        try {
            return (String) plat.getClass().getMethod("getImagePath").invoke(plat);
        } catch (Exception e) {
            return null;
        }
    }
}