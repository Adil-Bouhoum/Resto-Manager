package com.restaurant.controller;

import com.restaurant.model.Commande;
import com.restaurant.model.Paiement;
import com.restaurant.service.*;
import com.restaurant.service.CaisseService.PlatVente;
import com.restaurant.service.CaisseService.StatsJournee;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import java.time.LocalDate;
import java.util.List;

public class CaisseController extends BaseController {

    @FXML private TableView<Commande> commandesAPayerTable;
    @FXML private TableView<Commande> commandesPayeesTable;
    @FXML private TableView<PlatVente> topPlatsTable;

    @FXML private Label totalVentesLabel;
    @FXML private Label commandesPayeesLabel;
    @FXML private Label moyennePanierLabel;

    @FXML private TextField montantField;
    @FXML private ComboBox<String> modePaiementCombo;
    @FXML private Label totalAPayerLabel;
    @FXML private Label renduLabel;

    @FXML private DatePicker rapportDatePicker;

    private ObservableList<Commande> commandesAPayer = FXCollections.observableArrayList();
    private ObservableList<Commande> commandesPayees = FXCollections.observableArrayList();
    private ObservableList<PlatVente> topPlats = FXCollections.observableArrayList();

    // Setters
    public void setCaisseService(CaisseService caisseService) {
        this.caisseService = caisseService;
    }

    public void setCommandeService(CommandeService commandeService) {
        this.commandeService = commandeService;
    }

    @Override
    public void setServices(CarteService carteService,
                            SalleService salleService,
                            CommandeService commandeService,
                            CuisineService cuisineService,
                            CaisseService caisseService) {
        this.caisseService = caisseService;
        this.commandeService = commandeService;
        loadData(); // Charger après injection
    }

    @FXML
    private void initialize() {
        setupTables();
        setupListeners();

        // Initialiser le mode de paiement
        modePaiementCombo.getItems().addAll("ESPECES", "CARTE", "TICKET_RESTO", "CHEQUE");
        modePaiementCombo.getSelectionModel().selectFirst();

        // Date par défaut = aujourd'hui
        rapportDatePicker.setValue(LocalDate.now());
    }

    private void setupTables() {
        // Tableau Commandes à Payer
        TableColumn<Commande, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);

        TableColumn<Commande, Integer> tableCol = new TableColumn<>("Table");
        tableCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(cell.getValue().getTable().getNumeroTable()).asObject());
        tableCol.setPrefWidth(60);

        TableColumn<Commande, Double> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getTotalAvecRemise()).asObject());
        totalCol.setPrefWidth(80);
        totalCol.setCellFactory(col -> new TableCell<Commande, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f€", item));
                }
            }
        });

        commandesAPayerTable.getColumns().addAll(idCol, tableCol, totalCol);
        commandesAPayerTable.setItems(commandesAPayer);

        // Tableau Commandes Payées (similaire)
        commandesPayeesTable.getColumns().addAll(
                createColumn("ID", "id", 50),
                createColumn("Table", "table.numeroTable", 60),
                createColumn("Total", "totalAvecRemise", 80)
        );
        commandesPayeesTable.setItems(commandesPayees);

        // Tableau Top Plats
        TableColumn<PlatVente, String> platNomCol = new TableColumn<>("Plat");
        platNomCol.setCellValueFactory(new PropertyValueFactory<>("nomPlat"));

        TableColumn<PlatVente, Integer> quantiteCol = new TableColumn<>("Quantité");
        quantiteCol.setCellValueFactory(new PropertyValueFactory<>("quantiteVendue"));

        topPlatsTable.getColumns().addAll(platNomCol, quantiteCol);
        topPlatsTable.setItems(topPlats);
    }

    private TableColumn<Commande, ?> createColumn(String title, String property, double width) {
        TableColumn<Commande, ?> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setPrefWidth(width);
        return col;
    }

    private void loadData() {
        loadCommandesAPayer();
        loadCommandesPayees();
        loadStats();
    }

    private void loadCommandesAPayer() {
        try {
            List<Commande> commandes = caisseService.getCommandesAPayer();
            commandesAPayer.setAll(commandes);
        } catch (Exception e) {
            showError("Erreur", "Impossible de charger les commandes à payer");
        }
    }

    private void loadCommandesPayees() {
        try {
            List<Commande> commandes = caisseService.getCommandesPayeesAujourdhui();
            commandesPayees.setAll(commandes);
        } catch (Exception e) {
            showError("Erreur", "Impossible de charger les commandes payées");
        }
    }

    private void loadStats() {
        try {
            // Ventes du jour
            Double totalVentes = caisseService.getTotalVentesAujourdhui();
            totalVentesLabel.setText(String.format("%.2f€", totalVentes != null ? totalVentes : 0.0));

            // Nombre de commandes payées
            List<Commande> payees = caisseService.getCommandesPayeesAujourdhui();
            commandesPayeesLabel.setText(String.valueOf(payees.size()));

            // Moyenne panier
            if (!payees.isEmpty() && totalVentes != null && totalVentes > 0) {
                double moyenne = totalVentes / payees.size();
                moyennePanierLabel.setText(String.format("%.2f€", moyenne));
            } else {
                moyennePanierLabel.setText("0.00€");
            }

            // Top plats
            List<PlatVente> topPlatsList = caisseService.getTop5PlatsAujourdhui();
            topPlats.setAll(topPlatsList);

        } catch (Exception e) {
            showError("Erreur", "Impossible de charger les statistiques");
        }
    }

    private void setupListeners() {
        // Sélection d'une commande à payer
        commandesAPayerTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        updatePaymentInfo(newVal);
                    }
                }
        );

        // Changement du montant payé
        montantField.textProperty().addListener((obs, oldVal, newVal) -> {
            calculateRendu();
        });
    }

    private void updatePaymentInfo(Commande commande) {
        if (commande != null) {
            Double total = commande.getTotalAvecRemise();
            totalAPayerLabel.setText(String.format("%.2f€", total));
            calculateRendu();
        }
    }

    private void calculateRendu() {
        try {
            Commande selected = commandesAPayerTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                renduLabel.setText("0.00€");
                return;
            }

            String montantText = montantField.getText().trim();
            if (montantText.isEmpty()) {
                renduLabel.setText("0.00€");
                return;
            }

            Double montant = Double.parseDouble(montantText);
            Double total = selected.getTotalAvecRemise();

            if (montant >= total) {
                Double rendu = caisseService.calculateRendu(selected, montant);
                renduLabel.setText(String.format("%.2f€", rendu));
            } else {
                renduLabel.setText("Insuffisant");
            }
        } catch (NumberFormatException e) {
            renduLabel.setText("Montant invalide");
        } catch (Exception e) {
            renduLabel.setText("Erreur");
        }
    }

    @FXML
    private void handleProcessPayment() {
        Commande commande = commandesAPayerTable.getSelectionModel().getSelectedItem();
        if (commande == null) {
            showError("Erreur", "Veuillez sélectionner une commande");
            return;
        }

        String montantText = montantField.getText().trim();
        String modePaiement = modePaiementCombo.getValue();

        if (montantText.isEmpty()) {
            showError("Erreur", "Veuillez saisir le montant");
            return;
        }

        if (modePaiement == null) {
            showError("Erreur", "Veuillez sélectionner un mode de paiement");
            return;
        }

        try {
            Double montant = Double.parseDouble(montantText);

            // Enregistrer le paiement
            Paiement paiement = caisseService.recordPayment(commande, montant, modePaiement);

            // Afficher reçu
            showReceipt(commande, paiement);

            // Recharger les données
            loadData();
            clearPaymentFields();

            showInfo("Succès", "Paiement enregistré");

        } catch (NumberFormatException e) {
            showError("Erreur", "Montant invalide");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
        }
    }

    private void showReceipt(Commande commande, Paiement paiement) {
        Alert receipt = new Alert(Alert.AlertType.INFORMATION);
        receipt.setTitle("Reçu de Paiement");
        receipt.setHeaderText("Restaurant - Ticket de Caisse");

        StringBuilder sb = new StringBuilder();
        sb.append("================================\n");
        sb.append("Commande #").append(commande.getId()).append("\n");
        sb.append("Table: ").append(commande.getTable().getNumeroTable()).append("\n");
        sb.append("Date: ").append(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n");
        sb.append("--------------------------------\n");

        // Détails des plats
        sb.append("Détails:\n");
        commande.getLignes().forEach(ligne -> {
            sb.append(String.format("%dx %s", ligne.getQuantite(), ligne.getPlat().getNom()))
                    .append(String.format(" %6.2f€\n", ligne.getSousTotal()));
        });

        sb.append("--------------------------------\n");
        sb.append(String.format("Total:      %10.2f€\n", commande.getTotal()));

        if (commande.getRemiseAppliquee() != null && commande.getRemiseAppliquee() > 0) {
            sb.append(String.format("Remise:    -%9.2f€\n", commande.getRemiseAppliquee()));
        }

        sb.append(String.format("À payer:    %10.2f€\n", commande.getTotalAvecRemise()));
        sb.append("--------------------------------\n");
        sb.append(String.format("Payé:       %10.2f€\n", paiement.getMontant()));
        sb.append(String.format("Rendu:      %10.2f€\n", paiement.getMontant() - commande.getTotalAvecRemise()));
        sb.append("Mode: ").append(paiement.getModePaiement()).append("\n");
        sb.append("================================\n");
        sb.append("Merci de votre visite !\n");

        receipt.setContentText(sb.toString());

        // Option pour imprimer
        ButtonType printBtn = new ButtonType("Imprimer", ButtonBar.ButtonData.OTHER);
        receipt.getButtonTypes().add(printBtn);

        receipt.showAndWait().ifPresent(buttonType -> {
            if (buttonType == printBtn) {
                // Simple impression dans la console pour l'exemple
                System.out.println(sb.toString());
            }
        });
    }

    @FXML
    private void handleGenerateReport() {
        LocalDate date = rapportDatePicker.getValue();
        if (date == null) {
            showError("Erreur", "Veuillez sélectionner une date");
            return;
        }

        try {
            StatsJournee stats = caisseService.getStatsJournee();

            Alert report = new Alert(Alert.AlertType.INFORMATION);
            report.setTitle("Rapport Journalier");
            report.setHeaderText("Rapport du " + date);

            StringBuilder sb = new StringBuilder();
            sb.append("=== RAPPORT DU JOUR ===\n\n");
            sb.append("Date: ").append(date).append("\n");
            sb.append("Commandes totales: ").append(stats.getTotalCommandes()).append("\n");
            sb.append("Commandes payées: ").append(stats.getCommandesPayees()).append("\n");
            sb.append("Commandes en cours: ").append(stats.getCommandesEnCours()).append("\n");
            sb.append("Chiffre d'affaires: ").append(String.format("%.2f€", stats.getChiffreAffaires())).append("\n");
            sb.append("Panier moyen: ").append(String.format("%.2f€", stats.getMoyennePanier())).append("\n\n");

            sb.append("=== TOP 5 PLATS ===\n");
            if (stats.getTopPlats() != null && !stats.getTopPlats().isEmpty()) {
                for (PlatVente plat : stats.getTopPlats()) {
                    sb.append("- ").append(plat.getNomPlat())
                            .append(": ").append(plat.getQuantiteVendue()).append(" unités\n");
                }
            } else {
                sb.append("Aucune vente aujourd'hui\n");
            }

            report.setContentText(sb.toString());
            report.show();

        } catch (Exception e) {
            showError("Erreur", "Impossible de générer le rapport: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewCommandeDetails() {
        Commande selected = commandesAPayerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            selected = commandesPayeesTable.getSelectionModel().getSelectedItem();
        }

        if (selected != null) {
            showCommandeDetails(selected);
        } else {
            showError("Erreur", "Veuillez sélectionner une commande");
        }
    }

    private void showCommandeDetails(Commande commande) {
        Alert details = new Alert(Alert.AlertType.INFORMATION);
        details.setTitle("Détails Commande #" + commande.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(commande.getTable().getNumeroTable()).append("\n");
        sb.append("Statut: ").append(commande.getStatut()).append("\n");
        sb.append("Date: ").append(commande.getDateCreation()).append("\n\n");
        sb.append("Plats:\n");

        commande.getLignes().forEach(ligne -> {
            sb.append("- ").append(ligne.getQuantite())
                    .append("x ").append(ligne.getPlat().getNom())
                    .append(" (").append(ligne.getPrix()).append("€)")
                    .append(" = ").append(ligne.getSousTotal()).append("€\n");
        });

        sb.append("\nTotal: ").append(commande.getTotal()).append("€\n");
        if (commande.getRemiseAppliquee() != null && commande.getRemiseAppliquee() > 0) {
            sb.append("Remise: -").append(commande.getRemiseAppliquee()).append("€\n");
        }
        sb.append("À payer: ").append(commande.getTotalAvecRemise()).append("€\n");

        details.setContentText(sb.toString());
        details.show();
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    private void clearPaymentFields() {
        montantField.clear();
        renduLabel.setText("0.00€");
        totalAPayerLabel.setText("0.00€");
        commandesAPayerTable.getSelectionModel().clearSelection();
    }
}