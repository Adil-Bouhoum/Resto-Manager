package com.restaurant.controller;

import com.restaurant.model.Commande;
import com.restaurant.model.TableResto;
import com.restaurant.service.*;
import com.restaurant.service.SalleService.TableAvecStatut;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.List;

public class SalleController extends BaseController {

    @FXML private GridPane tablesGrid;
    @FXML private ListView<Commande> commandesList;
    @FXML private TextField tableNumberField;
    @FXML private TextField tableCapacityField;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        // UI seulement - pas de données ici
        // loadTables() sera appelé via setServices()
    }

    @Override
    public void setServices(CarteService carteService,
                            SalleService salleService,
                            CommandeService commandeService,
                            CuisineService cuisineService,
                            CaisseService caisseService) {
        this.carteService = carteService;
        this.salleService = salleService;
        this.commandeService = commandeService;
        this.cuisineService = cuisineService;
        this.caisseService = caisseService;

        // ✅ Charger les données APRÈS injection
        loadTables();
        updateStatus();
    }

    private void loadTables() {
        try {
            // ✅ Récupère les tables AVEC les commandes chargées
            List<TableAvecStatut> tables = salleService.getAllTablesWithStatus();
            displayTables(tables);
        } catch (Exception e) {
            System.out.println("[ERROR] loadTables: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", "Impossible de charger les tables");
        }
    }

    private void displayTables(List<TableAvecStatut> tables) {
        tablesGrid.getChildren().clear();

        int col = 0;
        int row = 0;
        int maxCol = 4;

        for (TableAvecStatut tableStatut : tables) {
            TableResto table = tableStatut.getTable();
            String statut = tableStatut.getStatut();

            System.out.println("[DEBUG DISPLAY] Table " + table.getNumeroTable() +
                    " - statut: " + statut +
                    " - commandes.size(): " + table.getCommandes().size());

            // Créer une vue de table
            StackPane tableView = createTableView(table, statut);

            tablesGrid.add(tableView, col, row);

            col++;
            if (col >= maxCol) {
                col = 0;
                row++;
            }
        }
    }

    private StackPane createTableView(TableResto table, String statut) {
        // Rectangle pour la table
        Rectangle rect = new Rectangle(80, 60);

        // Couleur selon statut (fourni par Service = fiable)
        switch (statut) {
            case "LIBRE":
                rect.setFill(Color.GREEN);
                break;
            case "OCCUPEE":
                rect.setFill(Color.RED);
                break;
            case "ATTENTE_PAIEMENT":
                rect.setFill(Color.ORANGE);
                break;
            default:
                rect.setFill(Color.GRAY);
        }

        rect.setStroke(Color.BLACK);

        // Texte avec numéro, capacité et statut
        Text text = new Text("Table " + table.getNumeroTable() +
                "\n" + table.getCapacite() + " pers\n" + statut);
        text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        StackPane stack = new StackPane(rect, text);
        stack.setPrefSize(80, 60);

        // Double-clic pour ouvrir/créer commande
        stack.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                System.out.println("[DEBUG] Double-clic Table " + table.getNumeroTable());
                handleTableClick(table);
            }
        });

        return stack;
    }

    private void handleTableClick(TableResto table) {
        try {
            System.out.println("[DEBUG] handleTableClick - Table: " + table.getNumeroTable() +
                    " - Commandes: " + table.getCommandes().size());

            if (!table.isOccupee()) {
                System.out.println("[DEBUG] Table LIBRE - Création nouvelle commande...");

                // Créer nouvelle commande
                Commande newCommande = salleService.startNewCommande(table);
                System.out.println("[SUCCESS] Commande créée #" + newCommande.getId());

                // ✅ IMPORTANT: Recharger TOUS les tables (pas juste la couleur)
                // Cela résoud le problème de détachement Hibernate
                loadTables();
                updateStatus();

                showInfo("Succès", "Nouvelle commande créée pour Table " + table.getNumeroTable());

                // Charger les commandes de cette table
                loadCommandesForTable(table);
            } else {
                System.out.println("[DEBUG] Table OCCUPÉE - Voir commande existante");

                // Voir commande existante
                Commande activeCommande = salleService.getActiveCommande(table);
                if (activeCommande != null) {
                    System.out.println("[DEBUG] Commande active #" + activeCommande.getId());
                    loadCommandeDetails(activeCommande);
                } else {
                    showError("Erreur", "Aucune commande active trouvée");
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] handleTableClick: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    private void loadCommandesForTable(TableResto table) {
        try {
            List<Commande> commandes = commandeService.getCommandesByTable(table.getId());
            commandesList.getItems().setAll(commandes);
        } catch (Exception e) {
            System.out.println("[ERROR] loadCommandesForTable: " + e.getMessage());
            showError("Erreur", "Impossible de charger les commandes");
        }
    }

    private void loadCommandeDetails(Commande commande) {
        try {
            // ✅ Charger l'écran commande dans un Dialog/Popup
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/commande-view.fxml"));
            Parent commandeView = loader.load();

            // Injecter les services au CommandeController
            CommandeController commandeController = loader.getController();
            commandeController.setServices(
                    carteService,
                    salleService,
                    commandeService,
                    cuisineService,
                    caisseService
            );

            // Charger la commande dans le controller
            commandeController.loadCommande(commande);

            // Afficher dans une fenêtre popup
            Stage stage = new Stage();
            stage.setTitle("Commande #" + commande.getId());
            stage.setScene(new Scene(commandeView, 600, 500));
            stage.show();

        } catch (Exception e) {
            System.out.println("[ERROR] loadCommandeDetails: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", "Impossible de charger la commande");
        }
    }

    @FXML
    private void handleAddTable() {
        try {
            int numero = Integer.parseInt(tableNumberField.getText());
            int capacite = Integer.parseInt(tableCapacityField.getText());

            salleService.createTable(numero, capacite);
            loadTables();
            updateStatus();
            clearTableFields();
            showInfo("Succès", "Table ajoutée");
        } catch (NumberFormatException e) {
            showError("Erreur", "Numéro et capacité doivent être des nombres");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        System.out.println("[DEBUG] Refresh tables");
        loadTables();
        updateStatus();
    }

    @FXML
    private void handleLiberateTable() {
        TableResto selected = getSelectedTableFromGrid();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner une table");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setContentText("Libérer la Table " + selected.getNumeroTable() + " ?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            try {
                salleService.liberateTable(selected);
                loadTables();
                updateStatus();
                showInfo("Succès", "Table libérée");
            } catch (Exception e) {
                showError("Erreur", e.getMessage());
            }
        }
    }

    private TableResto getSelectedTableFromGrid() {
        return null;  // À implémenter selon les besoins
    }

    private void updateStatus() {
        try {
            long occupees = salleService.countTablesOccupees();
            long libres = salleService.countTablesLibres();
            statusLabel.setText("Tables: " + occupees + " occupées, " + libres + " libres");
        } catch (Exception e) {
            System.out.println("[ERROR] updateStatus: " + e.getMessage());
            statusLabel.setText("Erreur chargement statut");
        }
    }

    private void clearTableFields() {
        tableNumberField.clear();
        tableCapacityField.clear();
    }
}