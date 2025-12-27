package com.restaurant;

import com.restaurant.config.DatabaseConfig;
import com.restaurant.config.ErrorLogger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

/**
 * Point d'entrée de l'application RestManager
 */
public class Main extends Application {

    private static final String APP_TITLE = "RestManager - Gestion de Restaurant";
    private static final int WINDOW_WIDTH = 1400;
    private static final int WINDOW_HEIGHT = 800;

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println("╔═══════════════════════════════════════╗");
            System.out.println("║   RestManager - Démarrage...          ║");
            System.out.println("╚═══════════════════════════════════════╝");

            // Initialiser la base de données
            System.out.println("[1/3] Initialisation BD...");
            initializeDatabase();

            // Charger la vue principale
            System.out.println("[2/3] Chargement interface...");
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/main-view.fxml")
            );
            Parent root = loader.load();

            // Configurer la scène
            System.out.println("[3/3] Configuration UI...");
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

            // Charger CSS si disponible
            try {
                String css = getClass().getResource("/css/styles.css").toExternalForm();
                scene.getStylesheets().add(css);
                System.out.println("✓ CSS chargé");
            } catch (Exception e) {
                System.out.println("⚠ CSS non trouvée (optionnel)");
            }

            // Configurer et afficher la fenêtre
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);

            // Maximiser la fenêtre
            primaryStage.setMaximized(true);

            // Gestion de la fermeture
            primaryStage.setOnCloseRequest(event -> {
                try {
                    stop();
                } catch (Exception e) {
                    ErrorLogger.logError("Main.onCloseRequest", e);
                }
            });

            primaryStage.show();

            System.out.println("✓ Application démarrée avec succès !");
            System.out.println("══════════════════════════════════════════");

        } catch (IOException e) {
            ErrorLogger.logError("Main.start - IOException", e);
            showErrorDialog("Erreur",
                    "Impossible de charger l'interface: " + e.getMessage());
        } catch (Exception e) {
            ErrorLogger.logError("Main.start - Exception générale", e);
            showErrorDialog("Erreur Critique",
                    "Erreur au démarrage: " + e.getMessage());
        }
    }

    /**
     * Affiche une boîte de dialogue d'erreur
     */
    private void showErrorDialog(String title, String message) {
        try {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle(title);
            alert.setHeaderText("Erreur de démarrage");
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception e) {
            // Fallback: afficher dans la console
            System.err.println(title + ": " + message);
        }
    }

    /**
     * Initialise la base de données
     */
    private void initializeDatabase() {
        try {
            // Tester la connexion BD
            DatabaseConfig.getEntityManager().close();
            System.out.println("✓ Base de données initialisée");
            System.out.println("  Fichier: ./data/restaurant.mv.db");
            System.out.println("  URL: jdbc:h2:./data/restaurant");
        } catch (Exception e) {
            ErrorLogger.logError("Main.initializeDatabase", e);
            throw new RuntimeException("Impossible d'initialiser la BD: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws Exception {
        System.out.println("\n╔═══════════════════════════════════════╗");
        System.out.println("║   Fermeture de l'application...       ║");
        System.out.println("╚═══════════════════════════════════════╝");

        // Nettoyer les ressources
        try {
            DatabaseConfig.shutdown();
            System.out.println("✓ Base de données fermée");
        } catch (Exception e) {
            ErrorLogger.logError("Main.stop - Database", e);
            System.err.println("⚠ Erreur fermeture BD: " + e.getMessage());
        }

        // Arrêter les services en cours
        try {
            // TODO: Arrêter CuisineService.autoRefresh() si nécessaire
            System.out.println("✓ Services arrêtés");
        } catch (Exception e) {
            ErrorLogger.logError("Main.stop - Services", e);
        }

        super.stop();
        System.out.println("✓ Application fermée");
    }

    /**
     * Point d'entrée du programme
     */
    public static void main(String[] args) {
        // Configurer un gestionnaire d'exceptions global
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            ErrorLogger.logError("UncaughtException in " + thread.getName(), (Exception) throwable);
            System.err.println("Erreur non gérée: " + throwable.getMessage());
        });

        // Lancer l'application JavaFX
        launch(args);
    }
}