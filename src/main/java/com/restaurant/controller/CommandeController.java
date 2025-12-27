package com.restaurant.controller;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.model.Categorie;
import com.restaurant.model.Commande;
import com.restaurant.model.LigneCommande;
import com.restaurant.model.Plat;
import com.restaurant.model.enums.StatutCommande;
import com.restaurant.service.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.util.List;

public class CommandeController extends BaseController {

    @FXML private Label commandeLabel;
    @FXML private Label totalLabel;
    @FXML private Label totalAvecRemiseLabel;
    @FXML private Label statutLabel;

    @FXML private ListView<LigneCommande> lignesListView;
    @FXML private ComboBox<Categorie> categorieCombo;
    @FXML private ComboBox<Plat> platCombo;

    @FXML private Spinner<Integer> quantiteSpinner;
    @FXML private Spinner<Integer> quantiteSpinnerAdd;

    @FXML private TextField remiseField;
    @FXML private Button applyDiscountBtn;
    @FXML private Button envoyerCuisineBtn;
    @FXML private Button annulerBtn;

    private Commande currentCommande;

    @FXML
    private void initialize() {
        System.out.println("[DEBUG] CommandeController.initialize()");

        quantiteSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1)
        );

        quantiteSpinnerAdd.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1)
        );

        initializeCategorieCombo();
        initializePlatCombo();
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
    }

    public void loadCommande(Commande commande) {
        try {
            currentCommande = commandeService.getCommandeById(commande.getId());

            commandeLabel.setText("Commande #" + currentCommande.getId()
                    + " - Table " + currentCommande.getTable().getNumeroTable());

            statutLabel.setText("Statut: " + currentCommande.getStatut());

            loadCategories();
            refreshLignes();
            updateTotals();

            boolean isEditable = currentCommande.getStatut() == StatutCommande.EN_ATTENTE;

            categorieCombo.setDisable(!isEditable);
            platCombo.setDisable(!isEditable);
            quantiteSpinnerAdd.setDisable(!isEditable);
            quantiteSpinner.setDisable(!isEditable);
            annulerBtn.setDisable(!isEditable);

            envoyerCuisineBtn.setDisable(
                    !isEditable || currentCommande.getLignes().isEmpty()
            );

        } catch (Exception e) {
            System.out.println("[ERROR] loadCommande: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", "Impossible de charger la commande");
        }
    }

    /* ===================== COMBO INITIALIZATION ===================== */

    private void initializeCategorieCombo() {
        categorieCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Categorie c) {
                return c != null ? c.getNom() : "";
            }

            @Override
            public Categorie fromString(String s) {
                return null;
            }
        });

        categorieCombo.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Categorie item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getNom());
            }
        });

        categorieCombo.setOnAction(e -> loadPlats());
    }

    private void initializePlatCombo() {
        platCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Plat p) {
                return p != null ? p.getNom() + " - " + p.getPrix() + "€" : "";
            }

            @Override
            public Plat fromString(String s) {
                return null;
            }
        });

        platCombo.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Plat item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : item.getNom() + " - " + item.getPrix() + "€");
            }
        });
    }

    /* ===================== DATA LOADING ===================== */

    private void loadCategories() {
        try {
            List<Categorie> categories = carteService.getAllCategories();
            categorieCombo.setItems(FXCollections.observableArrayList(categories));
        } catch (Exception e) {
            System.out.println("[ERROR] loadCategories: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", "Impossible de charger les catégories");
        }
    }

    private void loadPlats() {
        try {
            Categorie selected = categorieCombo.getValue();
            if (selected == null) {
                platCombo.setItems(FXCollections.observableArrayList());
                return;
            }

            List<Plat> plats = carteService.getPlatsByCategorie(selected);
            platCombo.setItems(FXCollections.observableArrayList(plats));
        } catch (Exception e) {
            System.out.println("[ERROR] loadPlats: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", "Impossible de charger les plats");
        }
    }

    private void refreshLignes() {
        ObservableList<LigneCommande> lignes =
                FXCollections.observableArrayList(currentCommande.getLignes());

        lignesListView.setItems(lignes);

        lignesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LigneCommande item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null :
                        String.format("%dx %s - %.2f€ (%.2f€)",
                                item.getQuantite(),
                                item.getPlat().getNom(),
                                item.getPrix(),
                                item.getSousTotal()));
            }
        });
    }

    /* ===================== ACTIONS ===================== */

    @FXML
    private void handleAddPlat() {
        try {
            Plat plat = platCombo.getValue();
            if (plat == null) {
                showError("Erreur", "Sélectionnez un plat");
                return;
            }

            Integer quantite = quantiteSpinnerAdd.getValue();
            if (quantite == null || quantite <= 0) {
                showError("Erreur", "Quantité invalide");
                return;
            }

            commandeService.addLigneCommande(currentCommande, plat, quantite);

            // ✅ Rafraîchir COMPLÈTEMENT la commande
            currentCommande = commandeService.getCommandeById(currentCommande.getId());

            System.out.println("[DEBUG] handleAddPlat - Commande #" + currentCommande.getId() +
                    " a " + currentCommande.getLignes().size() + " lignes");

            refreshLignes();
            updateTotals();
            platCombo.setValue(null);
            quantiteSpinnerAdd.getValueFactory().setValue(1);

            // ✅ Réactiver le bouton si commande n'est pas vide
            envoyerCuisineBtn.setDisable(currentCommande.getLignes().isEmpty());

            showInfo("Succès", "Plat ajouté");

        } catch (Exception e) {
            System.out.println("[ERROR] handleAddPlat: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleRemovePlat() {
        try {
            LigneCommande ligne = lignesListView.getSelectionModel().getSelectedItem();

            if (ligne == null) {
                showError("Erreur", "Sélectionnez une ligne");
                return;
            }

            commandeService.removeLigneCommande(currentCommande, ligne.getId());
            currentCommande = commandeService.getCommandeById(currentCommande.getId());
            refreshLignes();
            updateTotals();

            // ✅ Réactiver/Désactiver le bouton
            envoyerCuisineBtn.setDisable(currentCommande.getLignes().isEmpty());

            showInfo("Succès", "Plat supprimé");

        } catch (Exception e) {
            System.out.println("[ERROR] handleRemovePlat: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleUpdateQuantite() {
        try {
            LigneCommande ligne = lignesListView.getSelectionModel().getSelectedItem();

            if (ligne == null) {
                showError("Erreur", "Sélectionnez une ligne");
                return;
            }

            Integer nouvelleQuantite = quantiteSpinner.getValue();
            if (nouvelleQuantite == null || nouvelleQuantite <= 0) {
                showError("Erreur", "Quantité invalide");
                return;
            }

            commandeService.updateLigneQuantite(
                    currentCommande, ligne.getId(), nouvelleQuantite);

            currentCommande = commandeService.getCommandeById(currentCommande.getId());
            refreshLignes();
            updateTotals();

            showInfo("Succès", "Quantité mise à jour");

        } catch (Exception e) {
            System.out.println("[ERROR] handleUpdateQuantite: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleApplyDiscount() {
        try {
            String remiseText = remiseField.getText();
            if (remiseText == null || remiseText.isEmpty()) {
                showError("Erreur", "Entrez un montant");
                return;
            }

            double remise = Double.parseDouble(remiseText);
            commandeService.applyDiscount(currentCommande, remise);

            currentCommande = commandeService.getCommandeById(currentCommande.getId());
            updateTotals();
            remiseField.clear();

            showInfo("Succès", "Remise appliquée");

        } catch (NumberFormatException e) {
            showError("Erreur", "Format invalide");
        } catch (Exception e) {
            System.out.println("[ERROR] handleApplyDiscount: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleEnvoyerCuisine() {
        try {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmation");
            confirm.setContentText("Envoyer en cuisine ?");

            if (confirm.showAndWait().get() == ButtonType.OK) {
                commandeService.updateCommandeStatus(
                        currentCommande, StatutCommande.EN_PREPARATION);

                currentCommande = commandeService.getCommandeById(currentCommande.getId());
                loadCommande(currentCommande);

                showInfo("Succès", "Commande envoyée en cuisine");
            }
        } catch (Exception e) {
            System.out.println("[ERROR] handleEnvoyerCuisine: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleAnnuler() {
        try {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmation");
            confirm.setContentText("Annuler cette commande ?");

            if (confirm.showAndWait().get() == ButtonType.OK) {
                commandeService.annulerCommande(currentCommande);
                ((javafx.stage.Stage) annulerBtn.getScene().getWindow()).close();
            }
        } catch (Exception e) {
            System.out.println("[ERROR] handleAnnuler: " + e.getMessage());
            e.printStackTrace();
            showError("Erreur", e.getMessage());
        }
    }

    private void updateTotals() {
        totalLabel.setText(String.format("Total: %.2f€",
                commandeService.calculateTotal(currentCommande)));

        totalAvecRemiseLabel.setText(String.format("À payer: %.2f€",
                commandeService.calculateTotalAvecRemise(currentCommande)));
    }
}