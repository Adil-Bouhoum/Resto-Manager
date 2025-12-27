package com.restaurant.controller;

import com.restaurant.model.Commande;
import com.restaurant.service.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.List;

public class CuisineController extends BaseController {

    @FXML private TableView<Commande> attenteTable;
    @FXML private TableView<Commande> preparationTable;
    @FXML private TableView<Commande> pretTable;
    @FXML private TableView<Commande> serviTable;
    @FXML private Label statsLabel;

    private CuisineService cuisineService;

    // Setter pour le service
    public void setCuisineService(CuisineService cuisineService) {
        this.cuisineService = cuisineService;
        initializeAfterInjection();
    }

    // OU si vous utilisez BaseController.setServices()
    @Override
    public void setServices(CarteService carteService,
                            SalleService salleService,
                            CommandeService commandeService,
                            CuisineService cuisineService,
                            CaisseService caisseService) {
        this.cuisineService = cuisineService;
        initializeAfterInjection();
    }

    @FXML
    private void initialize() {
        // Configuration UI SEULEMENT
        setupTables();
        // NE PAS appeler loadCommandes() ou startAutoRefresh() ici
    }

    private void initializeAfterInjection() {
        if (cuisineService != null) {
            loadCommandes();
            cuisineService.startAutoRefresh();
        }
    }

    private void setupTables() {
        // ==================== TABLE ATTENTE ====================
        TableColumn<Commande, Integer> attenteIdCol = new TableColumn<>("ID");
        attenteIdCol.setPrefWidth(40);
        attenteIdCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getId().intValue()).asObject());

        TableColumn<Commande, Integer> attenteTableCol = new TableColumn<>("Table");
        attenteTableCol.setPrefWidth(50);
        attenteTableCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getTable().getNumeroTable()).asObject());

        TableColumn<Commande, String> attentePlatCol = new TableColumn<>("Plats");
        attentePlatCol.setPrefWidth(120);
        attentePlatCol.setCellValueFactory(cell -> {
            Commande cmd = cell.getValue();
            String plats = cmd.getLignes().stream()
                    .map(l -> l.getQuantite() + "x " + l.getPlat().getNom())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Vide");
            return new javafx.beans.property.SimpleStringProperty(plats);
        });

        attenteTable.getColumns().addAll(attenteIdCol, attenteTableCol, attentePlatCol);

        // ==================== TABLE PRÉPARATION ====================
        TableColumn<Commande, Integer> prepIdCol = new TableColumn<>("ID");
        prepIdCol.setPrefWidth(40);
        prepIdCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getId().intValue()).asObject());

        TableColumn<Commande, Integer> prepTableCol = new TableColumn<>("Table");
        prepTableCol.setPrefWidth(50);
        prepTableCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getTable().getNumeroTable()).asObject());

        TableColumn<Commande, String> prepPlatCol = new TableColumn<>("Plats");
        prepPlatCol.setPrefWidth(120);
        prepPlatCol.setCellValueFactory(cell -> {
            Commande cmd = cell.getValue();
            String plats = cmd.getLignes().stream()
                    .map(l -> l.getQuantite() + "x " + l.getPlat().getNom())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Vide");
            return new javafx.beans.property.SimpleStringProperty(plats);
        });

        preparationTable.getColumns().addAll(prepIdCol, prepTableCol, prepPlatCol);

        // ==================== TABLE PRÊT ====================
        TableColumn<Commande, Integer> pretIdCol = new TableColumn<>("ID");
        pretIdCol.setPrefWidth(40);
        pretIdCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getId().intValue()).asObject());

        TableColumn<Commande, Integer> pretTableCol = new TableColumn<>("Table");
        pretTableCol.setPrefWidth(50);
        pretTableCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getTable().getNumeroTable()).asObject());

        TableColumn<Commande, String> pretPlatCol = new TableColumn<>("Plats");
        pretPlatCol.setPrefWidth(120);
        pretPlatCol.setCellValueFactory(cell -> {
            Commande cmd = cell.getValue();
            String plats = cmd.getLignes().stream()
                    .map(l -> l.getQuantite() + "x " + l.getPlat().getNom())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Vide");
            return new javafx.beans.property.SimpleStringProperty(plats);
        });

        pretTable.getColumns().addAll(pretIdCol, pretTableCol, pretPlatCol);

        // ==================== TABLE SERVI ====================
        TableColumn<Commande, Integer> serviIdCol = new TableColumn<>("ID");
        serviIdCol.setPrefWidth(40);
        serviIdCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getId().intValue()).asObject());

        TableColumn<Commande, Integer> serviTableCol = new TableColumn<>("Table");
        serviTableCol.setPrefWidth(50);
        serviTableCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(
                        cell.getValue().getTable().getNumeroTable()).asObject());

        TableColumn<Commande, String> serviPlatCol = new TableColumn<>("Plats");
        serviPlatCol.setPrefWidth(120);
        serviPlatCol.setCellValueFactory(cell -> {
            Commande cmd = cell.getValue();
            String plats = cmd.getLignes().stream()
                    .map(l -> l.getQuantite() + "x " + l.getPlat().getNom())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Vide");
            return new javafx.beans.property.SimpleStringProperty(plats);
        });

        serviTable.getColumns().addAll(serviIdCol, serviTableCol, serviPlatCol);
    }

    private void loadCommandes() {
        try {
            attenteTable.setItems(cuisineService.getCommandesEnAttenteList());
            preparationTable.setItems(cuisineService.getCommandesEnPreparationList());
            pretTable.setItems(cuisineService.getCommandesPretList());
            serviTable.setItems(cuisineService.getCommandesServiList());

            updateStats();
        } catch (Exception e) {
            showError("Erreur", "Impossible de charger les commandes");
        }
    }

    private void startAutoRefresh() {
        if (cuisineService != null) {
            cuisineService.startAutoRefresh();
        }
    }


    private void updateStats() {
        int attente = attenteTable.getItems().size();
        int preparation = preparationTable.getItems().size();
        int pret = pretTable.getItems().size();           // ✅ NOUVEAU
        int servi = serviTable.getItems().size();         // ✅ NOUVEAU

        statsLabel.setText("Attente: " + attente +
                " | Préparation: " + preparation +
                " | Prêt: " + pret +             // ✅ NOUVEAU
                " | Servi: " + servi);           // ✅ NOUVEAU
    }

    @FXML
    private void handleSendToPreparation() {
        Commande selected = attenteTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner une commande en attente");
            return;
        }

        try {
            cuisineService.envoyerEnPreparation(selected);
            loadCommandes();
            showInfo("Succès", "Commande envoyée en préparation");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleMarkReady() {
        // ✅ Chercher dans preparationTable (EN_PREPARATION → PRET)
        Commande selected = preparationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner une commande en préparation");
            return;
        }

        try {
            cuisineService.marquerPrete(selected);
            loadCommandes();
            showInfo("Succès", "Commande marquée comme prête");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleMarkServed() {
        // ✅ Chercher dans pretTable (PRET → SERVI)
        Commande selected = pretTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner une commande prête");
            return;
        }

        try {
            cuisineService.marquerServie(selected);
            loadCommandes();
            showInfo("Succès", "Commande marquée comme servie");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleViewDetails() {
        // ✅ Chercher dans toutes les tables
        Commande selected = attenteTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            selected = preparationTable.getSelectionModel().getSelectedItem();
        }
        if (selected == null) {
            selected = pretTable.getSelectionModel().getSelectedItem();
        }
        if (selected == null) {
            selected = serviTable.getSelectionModel().getSelectedItem();
        }

        if (selected != null) {
            showCommandeDetails(selected);
        } else {
            showError("Erreur", "Veuillez sélectionner une commande");
        }
    }

    @FXML
    private void handleRefresh() {
        loadCommandes();
    }

    private void showCommandeDetails(Commande commande) {
        Alert details = new Alert(Alert.AlertType.INFORMATION);
        details.setTitle("Détails Commande #" + commande.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(commande.getTable().getNumeroTable()).append("\n");
        sb.append("Statut: ").append(commande.getStatut()).append("\n");
        sb.append("Total: ").append(commande.getTotalAvecRemise()).append("€\n\n");
        sb.append("Plats:\n");

        commande.getLignes().forEach(ligne ->
                sb.append("- ").append(ligne.getQuantite())
                        .append("x ").append(ligne.getPlat().getNom())
                        .append(" = ").append(ligne.getSousTotal()).append("€\n")
        );

        details.setContentText(sb.toString());
        details.show();
    }

    public void onSceneClose() {
        if (cuisineService != null) {
            System.out.println("[DEBUG] CuisineController - Arrêt du scheduler");
            cuisineService.stopAutoRefresh();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        onSceneClose();
        super.finalize();
    }
}