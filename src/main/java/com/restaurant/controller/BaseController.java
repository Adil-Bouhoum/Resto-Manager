package com.restaurant.controller;

import com.restaurant.service.*;

public abstract class BaseController {

    protected CarteService carteService;
    protected SalleService salleService;
    protected CommandeService commandeService;
    protected CuisineService cuisineService;
    protected CaisseService caisseService;

    public void setServices(
            CarteService carteService,
            SalleService salleService,
            CommandeService commandeService,
            CuisineService cuisineService,
            CaisseService caisseService
    ) {
        this.carteService = carteService;
        this.salleService = salleService;
        this.commandeService = commandeService;
        this.cuisineService = cuisineService;
        this.caisseService = caisseService;
    }

    protected void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle(title);
        alert.setContentText(message);
        alert.show();
    }

    protected void showInfo(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION
        );
        alert.setTitle(title);
        alert.setContentText(message);
        alert.show();
    }
}