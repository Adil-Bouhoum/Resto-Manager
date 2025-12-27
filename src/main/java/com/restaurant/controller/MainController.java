package com.restaurant.controller;

import com.restaurant.service.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import java.io.IOException;

public class MainController {

    @FXML private BorderPane mainBorderPane;

    // Services partagés
    private CarteService carteService;
    private SalleService salleService;
    private CommandeService commandeService;
    private CuisineService cuisineService;
    private CaisseService caisseService;
    private Object currentController;

    public MainController() {
        // Initialiser les services UNE SEULE FOIS
        this.carteService = new CarteService();
        this.salleService = new SalleService();
        this.commandeService = new CommandeService();
        this.cuisineService = new CuisineService();
        this.caisseService = new CaisseService();
    }

    @FXML
    private void initialize() {
        // Charger l'écran d'accueil par défaut
        switchToSalle();
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // ✅ Arrêter le scheduler Cuisine avant de changer de view
            if (currentController instanceof CuisineController) {
                ((CuisineController) currentController).onSceneClose();
            }

            // Injecter les services
            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setServices(
                        carteService,
                        salleService,
                        commandeService,
                        cuisineService,
                        caisseService
                );
            }

            currentController = controller;
            mainBorderPane.setCenter(view);

        } catch (IOException e) {
            e.printStackTrace();
            showError("Erreur", "Impossible de charger: " + fxmlPath);
        }
    }


    @FXML
    private void switchToCarte() {
        loadView("/views/carte-view.fxml");
    }

    @FXML
    private void switchToSalle() {
        loadView("/views/salle-view.fxml");
    }

    @FXML
    private void switchToCuisine() {
        loadView("/views/cuisine-view.fxml");
    }

    @FXML
    private void switchToCaisse() {
        loadView("/views/caisse-view.fxml");
    }

    @FXML
    private void handleExit() {
        System.exit(0);
    }

    private void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle(title);
        alert.setContentText(message);
        alert.show();
    }


}