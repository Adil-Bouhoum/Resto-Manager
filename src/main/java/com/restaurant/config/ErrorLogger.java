package com.restaurant.config;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.restaurant.exception.ValidationException;
import com.restaurant.exception.DatabaseException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ErrorLogger {
    private static final Logger logger = LoggerFactory.getLogger(ErrorLogger.class);
    private static final String ERROR_DOC_FILE = "./logs/error_documentation.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        // Créer le dossier logs s'il n'existe pas
        File logsDir = new File("./logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
    }

    /**
     * Log une erreur avec contexte + sauvegarde dans fichier d'erreurs
     */
    public static void logError(String context, Exception e) {
        String logMessage = formatErrorMessage(context, e);

        // Log SLF4J (Logback)
        logger.error(logMessage);

        // Sauvegarde fichier documentation
        writeToErrorDoc(logMessage);

        // Alert JavaFX pour erreurs critiques (SEULEMENT si JavaFX est actif)
        try {
            if (Platform.isFxApplicationThread()) {
                showErrorAlert(e);
            } else if (isJavaFXAvailable()) {
                Platform.runLater(() -> showErrorAlert(e));
            }
        } catch (Exception ignored) {
            // JavaFX pas initialisé (tests, mode CLI), on ignore
        }
    }

    /**
     * Vérifier si JavaFX est disponible et initialisé
     */
    private static boolean isJavaFXAvailable() {
        try {
            // Essayer d'accéder à javafx.application.Platform
            Class.forName("javafx.application.Platform");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Log simple (INFO)
     */
    public static void logInfo(String message) {
        logger.info(message);
    }

    /**
     * Log simple (DEBUG)
     */
    public static void logDebug(String message) {
        logger.debug(message);
    }

    /**
     * Format le message d'erreur avec timestamp, contexte et stack trace
     */
    private static String formatErrorMessage(String context, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        return String.format(
                "[%s] CONTEXT: %s\nException: %s\nMessage: %s\n\nStack Trace:\n%s",
                LocalDateTime.now().format(formatter),
                context,
                e.getClass().getSimpleName(),
                e.getMessage() != null ? e.getMessage() : "No message",
                sw.toString()
        );
    }

    /**
     * Écrit dans le fichier d'erreurs
     */
    private static void writeToErrorDoc(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ERROR_DOC_FILE, true))) {
            writer.write("=".repeat(100));
            writer.newLine();
            writer.write(message);
            writer.newLine();
            writer.newLine();
        } catch (IOException ioEx) {
            logger.error("Impossible d'écrire dans le fichier d'erreurs", ioEx);
        }
    }

    /**
     * Affiche une Alert JavaFX selon le type d'exception
     */
    private static void showErrorAlert(Exception e) {
        Alert alert;

        if (e instanceof ValidationException) {
            alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("⚠️ Erreur de Validation");
            alert.setHeaderText("Données invalides");
            alert.setContentText(e.getMessage());
        } else if (e instanceof DatabaseException) {
            alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("❌ Erreur Base de Données");
            alert.setHeaderText("Problème d'accès à la base de données");
            alert.setContentText("Vérifiez les logs pour plus de détails.\n" + e.getMessage());
        } else {
            alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("❌ Erreur Système");
            alert.setHeaderText("Une erreur inattendue s'est produite");
            alert.setContentText(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        alert.showAndWait();
    }
}