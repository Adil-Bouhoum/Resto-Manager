package com.restaurant.controller;

import com.restaurant.model.Categorie;
import com.restaurant.model.Plat;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.ValidationException;
import com.restaurant.service.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;

public class CarteController extends BaseController {

    @FXML private ListView<Categorie> categoriesList;
    @FXML private ListView<Plat> platsList;
    @FXML private ComboBox<Categorie> categorieCombo;

    @FXML private TextField nomCategorieField;
    @FXML private TextArea descCategorieField;

    @FXML private TextField nomPlatField;
    @FXML private TextField prixPlatField;
    @FXML private TextArea descPlatField;

    @FXML private ImageView platImageView;
    @FXML private Button uploadImageBtn;

    private File selectedImageFile;
    private CarteService carteService;

    // Setter
    public void setCarteService(CarteService carteService) {
        this.carteService = carteService;
        loadCategories(); // Charger après injection
    }

    @Override
    public void setServices(CarteService carteService,
                            SalleService salleService,
                            CommandeService commandeService,
                            CuisineService cuisineService,
                            CaisseService caisseService) {
        this.carteService = carteService;
        loadCategories(); // Charger après injection
    }



    @FXML
    private void initialize() {
        // Ne plus charger ici, attendre setCarteService()
        if (carteService != null) {
            loadCategories();
        }
    }


    private void loadCategories() {
        try {
            List<Categorie> categories = carteService.getAllCategories();
            categoriesList.getItems().setAll(categories);
            categorieCombo.getItems().setAll(categories);

            if (!categories.isEmpty()) {
                categoriesList.getSelectionModel().selectFirst();
                categorieCombo.getSelectionModel().selectFirst();
            }
        } catch (DatabaseException e) {
            showError("Erreur", "Impossible de charger les catégories");
        }
    }

    private void loadPlatsByCategorie() {
        Categorie selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                List<Plat> plats = carteService.getPlatsByCategorie(selected);
                platsList.getItems().setAll(plats);
            } catch (DatabaseException e) {
                showError("Erreur", "Impossible de charger les plats");
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setupListeners() {
        categoriesList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        loadPlatsByCategorie();
                    }
                }
        );

        platsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        displayPlatDetails(newVal);
                    }
                }
        );
    }

    private void displayPlatDetails(Plat plat) {
        nomPlatField.setText(plat.getNom());
        prixPlatField.setText(String.valueOf(plat.getPrix()));
        descPlatField.setText(plat.getDescription());

        // Afficher l'image
        if (plat.getImageFX() != null) {
            platImageView.setImage(plat.getImageFX());
        }

        // Sélectionner la catégorie
        categorieCombo.getSelectionModel().select(plat.getCategorie());
    }

    @FXML
    private void handleAddCategorie() {
        String nom = nomCategorieField.getText().trim();
        String desc = descCategorieField.getText().trim();

        if (nom.isEmpty()) {
            showError("Erreur", "Le nom de la catégorie est requis");
            return;
        }

        try {
            carteService.addCategorie(nom, desc);
            loadCategories();
            clearCategorieFields();
            showInfo("Succès", "Catégorie ajoutée");
        } catch (ValidationException | DatabaseException e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleUpdateCategorie() {
        Categorie selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner une catégorie");
            return;
        }

        String nom = nomCategorieField.getText().trim();
        String desc = descCategorieField.getText().trim();

        try {
            carteService.updateCategorie(selected.getId(), nom, desc);
            loadCategories();
            showInfo("Succès", "Catégorie modifiée");
        } catch (ValidationException | DatabaseException e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteCategorie() {
        Categorie selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner une catégorie");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setContentText("Supprimer la catégorie " + selected.getNom() + " ?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            try {
                carteService.deleteCategorie(selected.getId());
                loadCategories();
                showInfo("Succès", "Catégorie supprimée");
            } catch (ValidationException | DatabaseException e) {
                showError("Erreur", e.getMessage());
            }
        }
    }

    @FXML
    private void handleAddPlat() {
        Categorie categorie = categorieCombo.getValue();
        if (categorie == null) {
            showError("Erreur", "Veuillez sélectionner une catégorie");
            return;
        }

        String nom = nomPlatField.getText().trim();
        String prixText = prixPlatField.getText().trim();
        String desc = descPlatField.getText().trim();

        if (nom.isEmpty() || prixText.isEmpty()) {
            showError("Erreur", "Nom et prix requis");
            return;
        }

        try {
            double prix = Double.parseDouble(prixText);

            Plat plat;
            if (selectedImageFile != null) {
                plat = carteService.addPlatWithImage(nom, prix, categorie, desc, selectedImageFile);
            } else {
                plat = carteService.addPlat(nom, prix, categorie, desc);
            }

            loadPlatsByCategorie();
            clearPlatFields();
            showInfo("Succès", "Plat ajouté");
        } catch (NumberFormatException e) {
            showError("Erreur", "Prix invalide");
        } catch (ValidationException | DatabaseException e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleUpdatePlat() {
        Plat selected = platsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner un plat");
            return;
        }

        Categorie categorie = categorieCombo.getValue();
        String nom = nomPlatField.getText().trim();
        String prixText = prixPlatField.getText().trim();
        String desc = descPlatField.getText().trim();

        try {
            double prix = Double.parseDouble(prixText);
            carteService.updatePlat(selected.getId(), nom, prix, categorie, desc, selectedImageFile);

            loadPlatsByCategorie();
            showInfo("Succès", "Plat modifié");
        } catch (NumberFormatException e) {
            showError("Erreur", "Prix invalide");
        } catch (ValidationException | DatabaseException e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleDeletePlat() {
        Plat selected = platsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Erreur", "Veuillez sélectionner un plat");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setContentText("Supprimer le plat " + selected.getNom() + " ?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            try {
                carteService.deletePlat(selected.getId());
                loadPlatsByCategorie();
                clearPlatFields();
                showInfo("Succès", "Plat supprimé");
            } catch (ValidationException | DatabaseException e) {
                showError("Erreur", e.getMessage());
            }
        }
    }

    @FXML
    private void handleUploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif")
        );

        File file = fileChooser.showOpenDialog(uploadImageBtn.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            platImageView.setImage(new javafx.scene.image.Image(file.toURI().toString()));
        }
    }

    @FXML
    private void handleRefresh() {
        loadCategories();
        clearCategorieFields();
        clearPlatFields();
    }

    private void clearCategorieFields() {
        nomCategorieField.clear();
        descCategorieField.clear();
    }

    private void clearPlatFields() {
        nomPlatField.clear();
        prixPlatField.clear();
        descPlatField.clear();
        platImageView.setImage(null);
        selectedImageFile = null;
    }
}