# 📖 Documentation RestManager

## Table des matières
1. [Vue d'ensemble](#vue-densemble)
2. [Architecture](#architecture)
3. [Workflow applicatif](#workflow-applicatif)
4. [Structure du projet](#structure-du-projet)
5. [Relations entre classes](#relations-entre-classes)
6. [Spécifications techniques](#spécifications-techniques)

---

## 🎯 Vue d'ensemble

**RestManager** est une application JavaFX de gestion de restaurant en temps réel. Elle permet de :

- ✅ **Gérer le menu** : Catégories et plats avec images
- ✅ **Gérer les tables** : État en temps réel (LIBRE/OCCUPÉE/ATTENTE_PAIEMENT)
- ✅ **Créer des commandes** : Ajouter/modifier plats, appliquer remises
- ✅ **Orchestrer la cuisine** : Workflow EN_ATTENTE → EN_PRÉPARATION → PRÊT → SERVI
- ✅ **Gérer les paiements** : Enregistrer paiements, libérer tables
- ✅ **Générer des rapports** : Statistiques ventes, plats populaires

**Stack technique** : Java 17 + JavaFX + Hibernate + H2 + Maven

---

## 🏗️ Architecture

### Pattern MVC + Couches de services

L'application suit une **architecture en couches** :

```
┌─────────────────────────────────────┐
│         LAYER PRÉSENTATION          │
│  Controllers + Views (FXML)         │
│  (CarteController, SalleController) │
└────────────┬────────────────────────┘
             ▼
┌─────────────────────────────────────┐
│      LAYER MÉTIER (SERVICES)        │
│  Logique applicative + Validations  │
│  (CarteService, SalleService)       │
└────────────┬────────────────────────┘
             ▼
┌─────────────────────────────────────┐
│    LAYER PERSISTENCE (DAO)          │
│  Requêtes BD + Transactions         │
│  (GenericDAO, CommandeDAO)          │
└────────────┬────────────────────────┘
             ▼
┌─────────────────────────────────────┐
│      LAYER DATA (Base de données)   │
│  H2 Database (Fichier persistant)   │
└─────────────────────────────────────┘
```

### Principes appliqués

- **Séparation des responsabilités** : Chaque couche a un rôle distinct
- **Dependency Injection** : Services injectés dans les controllers via `setServices()`
- **Transaction Management** : Toutes les opérations BD sont transactionnelles
- **Error Handling** : Exceptions métier + logging centralisé
- **Lazy Loading avec FETCH** : Évite LazyInitializationException

---

## 🔄 Workflow applicatif

### 1️⃣ Gestion du Menu (CarteController → CarteService → DAO)

```
UTILISATEUR                    CONTROLLER              SERVICE                 DAO
    │                              │                       │                     │
    ├─ Ajouter catégorie ────────► │                       │                     │
    │                              ├─ Valider unique ─────► │                     │
    │                              ├─ Créer TX ────────────────────────────────► │
    │                              │                       │  INSERT BD          │
    │                              │◄──────── Résultat ─────────────────────────┤
    │                              │                       │                     │
    │◄──── Afficher dans liste ───┤                       │                     │
    │
    ├─ Ajouter plat à catégorie ──► │                       │                     │
    │                              ├─ Valider prix > 0 ───► │                     │
    │                              ├─ Gérer image ────────► │  Base64 + File      │
    │                              ├─ Créer TX ────────────────────────────────► │
    │                              │◄──────── INSERT ───────────────────────────┤
    │◄──── Afficher dans liste ───┤                       │                     │
```

**Responsabilité de chaque couche** :
- **Controller** : Récupère inputs UI, affiche résultats, gère dialogs
- **Service** : Valide données, applique règles métier, gère transactions
- **DAO** : Exécute requêtes SQL, gère Hibernate, fetch les relations

---

### 2️⃣ Workflow Salle (SalleController → SalleService → DAO)

```
ÉTAPE 1 : Affichage tables
├─ SalleService.getAllTables()
│  └─ SalleDAO avec LEFT JOIN FETCH commandes
│     └─ Charge table + ses commandes active
├─ Calcul statut : isOccupee() retourne LIBRE/OCCUPÉE/ATTENTE_PAIEMENT
└─ Affichage : VERT (libre) | ROUGE (occupée) | ORANGE (attente paiement)

ÉTAPE 2 : Double-clic sur table LIBRE
├─ SalleService.startNewCommande(table)
│  ├─ TX Begin
│  ├─ Créer Commande(EN_ATTENTE)
│  ├─ Lier à table
│  └─ TX Commit
├─ refreshAll() recharge tables avec entityManager.clear()
└─ Table devient ROUGE automatiquement

ÉTAPE 3 : Double-clic sur table OCCUPÉE
├─ Ouvrir DialogWindow Commande
└─ Voir/Modifier commande existante
```

**Point clé** : `entityManager.clear()` dans `refreshAll()` force le rechargement de la BD

---

### 3️⃣ Workflow Commande (CommandeController → CommandeService → DAO)

```
COMMANDE (EN_ATTENTE)
    ▼
Ajouter plats ──► LigneCommande (Quantité + Prix snapshot)
    ▼
Modifier quantité / Supprimer ligne ──► Update BD
    ▼
Appliquer remise (max 50% du total)
    ▼
[BOUTON] Envoyer en cuisine
    │
    └─► CommandeService.updateCommandeStatus(EN_PREPARATION)
        ├─ TX Begin
        ├─ Vérifier transition valide (EN_ATTENTE → EN_PREPARATION autorisée)
        ├─ Vérifier commande non vide
        └─ TX Commit
```

**Transitions valides** :
- EN_ATTENTE → EN_PRÉPARATION (en salle)
- EN_PRÉPARATION → PRÊT (en cuisine)
- PRÊT → SERVI (en cuisine)
- SERVI → PAYÉE (en caisse)
- PAYÉE → FINALISÉE (libération table)

---

### 4️⃣ Workflow Cuisine (CuisineService temps réel)

```
AUTO-REFRESH TOUTES LES 2 SECONDES
├─ refreshAll() crée nouvel EntityManager
│  └─ ScheduledExecutorService exécute dans thread séparé
├─ Requête : SELECT COMMANDE WHERE statut IN (EN_ATTENTE, EN_PREP, PRET, SERVI)
│  └─ LEFT JOIN FETCH table, lignes, plat (évite LazyInitializationException)
├─ Platform.runLater() retour UI thread
└─ ObservableLists mises à jour ──► TableViews rafraîchies

ACTIONS UTILISATEUR
├─ Sélectionner EN_ATTENTE → [➡️] Envoyer en préparation
│  └─ TX : statut EN_ATTENTE → EN_PRÉPARATION
├─ Sélectionner EN_PRÉPARATION → [✅] Marquer prêt
│  └─ TX : statut EN_PRÉPARATION → PRÊT
└─ Sélectionner PRÊT → [🍽️] Marquer servi
   └─ TX : statut PRÊT → SERVI
```

**Point critique** : 
- ✅ Nouvel EM chaque refresh = pas de stale data
- ✅ LEFT JOIN FETCH table + lignes + plat = 1 requête
- ✅ ScheduledExecutorService arrêté proprement au quitter
- ✅ `stopAutoRefresh()` appelé avant de fermer l'écran Cuisine

---

### 5️⃣ Workflow Caisse (CaisseService → Paiement)

```
COMMANDE (SERVI)
    ▼
CaisseController affiche commandes à payer
    ▼
Sélectionner commande ──► Afficher montant
    ▼
Mode paiement : CARTE / ESPÈCES / CHÈQUE
    ▼
[Enregistrer Paiement]
    │
    ├─ TX Begin
    ├─ Créer Paiement(montant, mode, date)
    ├─ Lier à Commande
    ├─ Commande.statut = PAYÉE
    └─ TX Commit
    ▼
Table devient ORANGE (attente libération)
    ▼
[Libérer Table]
    │
    ├─ TX Begin
    ├─ Commande.statut = FINALISÉE
    └─ TX Commit
    ▼
Table redevient VERTE (libre)
```

---

## 📁 Structure du projet

### 1. `config/` - Configuration globale

```java
DatabaseConfig.java
├─ Singleton EntityManagerFactory
├─ getEntityManager() : Crée/retourne EM
└─ Utilisé partout pour DB access

ImageManager.java
├─ imageToBase64(File)
├─ saveImageFile(base64, filename)
└─ deleteImageFile(path)

ErrorLogger.java
├─ logError(context, exception)
└─ Écrit dans logs/ + console
```

### 2. `model/` - Entités JPA

```
BaseEntity (classe abstraite)
├─ id : Long (@Id @GeneratedValue)
├─ dateCreation : LocalDateTime
└─ dateModification : LocalDateTime

Categorie
├─ nom : String (unique)
├─ description : String
└─ plats : List<Plat> (@OneToMany)

Plat
├─ nom : String
├─ prix : Double
├─ description : String
├─ imageBase64 : String
├─ imagePath : String
├─ categorie : Categorie (@ManyToOne)
└─ lignesCommande : List<LigneCommande>

TableResto
├─ numeroTable : Integer (unique)
├─ capacite : Integer
├─ commandes : List<Commande> (@OneToMany)
└─ isOccupee() : boolean (calculée)

Commande
├─ statut : StatutCommande (EN_ATTENTE, EN_PRÉPARATION, PRÊT, SERVI, PAYÉE, FINALISÉE)
├─ dateCommande : LocalDateTime
├─ dateServi : LocalDateTime
├─ remiseAppliquee : Double
├─ table : TableResto (@ManyToOne)
├─ lignes : List<LigneCommande> (@OneToMany)
└─ paiements : List<Paiement> (@OneToMany)

LigneCommande
├─ quantite : Integer
├─ prix : Double (snapshot)
├─ commande : Commande (@ManyToOne)
└─ plat : Plat (@ManyToOne)

Paiement
├─ montant : Double
├─ modePaiement : String (CARTE, ESPÈCES, CHÈQUE)
├─ datePaiement : LocalDateTime
├─ reference : String (numéro transaction)
└─ commande : Commande (@ManyToOne)

StatutCommande (ENUM)
├─ EN_ATTENTE
├─ EN_PRÉPARATION
├─ PRÊT
├─ SERVI
├─ PAYÉE
├─ FINALISÉE
├─ ANNULÉE
└─ estEnCours() : boolean
```

### 3. `dao/` - Accès aux données

```java
GenericDAO<T extends BaseEntity>
├─ findById(id) : T
├─ findAll() : List<T>
├─ save(entity) : T
├─ delete(id) : void
├─ delete(entity) : void
└─ count() : long

Tous les DAO héritent de GenericDAO
├─ CategorieDAO
│  ├─ findByNom(nom) : Categorie
│  └─ searchByName(term) : List<Categorie>
├─ PlatDAO
│  ├─ findByCategorie(cat) : List<Plat>
│  └─ findMostPopular(limit) : List<Plat>
├─ TableDAO
│  ├─ findByNumero(num) : TableResto
│  └─ findAll() : List<TableResto> avec LEFT JOIN FETCH commandes
├─ CommandeDAO
│  ├─ findByStatut(statut) : List<Commande> avec FETCH table+lignes+plat
│  ├─ findByTable(table) : List<Commande>
│  ├─ findByTableId(id) : List<Commande>
│  └─ findAll() : List<Commande>
└─ PaiementDAO
   └─ findByCommande(cmd) : List<Paiement>
```

**Point technique important** :
- `findByStatut()` utilise `LEFT JOIN FETCH c.table LEFT JOIN FETCH c.lignes l LEFT JOIN FETCH l.plat`
- Évite LazyInitializationException lors de l'accès aux relations
- Créé dans nouvel EntityManager pour éviter problèmes de session

### 4. `service/` - Logique métier

```java
CarteService
├─ addCategorie(nom, description) : Categorie
├─ updateCategorie(id, nom, desc) : Categorie
├─ deleteCategorie(id) : void
├─ addPlat(nom, prix, cat, desc) : Plat
├─ addPlatWithImage(...) : Plat
├─ updatePlat(...) : Plat
├─ deletePlat(id) : void
├─ getAllPlats() : List<Plat>
├─ getPlatsByCategorie(cat) : List<Plat>
└─ searchPlatsByName(term) : List<Plat>

SalleService
├─ getAllTables() : List<TableResto> avec FETCH commandes
├─ getTableById(id) : TableResto avec FETCH commandes
├─ getTableStatus(table) : String (LIBRE/OCCUPÉE/ATTENTE_PAIEMENT)
├─ createTable(numero, capacite) : TableResto
├─ startNewCommande(table) : Commande
├─ getActiveCommande(table) : Commande
├─ liberateTable(table) : void
├─ getAllTablesWithStatus() : List<TableAvecStatut>
├─ countTablesOccupees() : long
└─ countTablesLibres() : long

CommandeService
├─ addLigneCommande(cmd, plat, qty) : void
├─ removeLigneCommande(cmd, ligneId) : void
├─ updateLigneQuantite(cmd, ligneId, qty) : void
├─ calculateTotal(cmd) : Double
├─ calculateTotalAvecRemise(cmd) : Double
├─ applyDiscount(cmd, discount) : void
├─ updateCommandeStatus(cmd, newStatus) : void (avec validation transitions)
├─ getCommandeById(id) : Commande
├─ annulerCommande(cmd) : void
└─ getCommandesByStatut(statut) : List<Commande>

CuisineService
├─ startAutoRefresh() : void (ScheduledExecutorService, 2 secondes)
├─ stopAutoRefresh() : void (arrête proprement scheduler)
├─ refreshAll() : void (avec nouvel EM)
├─ envoyerEnPreparation(cmd) : void
├─ marquerPrete(cmd) : void
├─ marquerServie(cmd) : void
├─ getCommandesEnAttenteList() : ObservableList<Commande>
├─ getCommandesEnPreparationList() : ObservableList<Commande>
├─ getCommandesPretList() : ObservableList<Commande>
└─ getCommandesServiList() : ObservableList<Commande>

CaisseService
├─ enregistrerPaiement(cmd, montant, mode) : Paiement
├─ getCommandesAPayerList() : ObservableList<Commande>
├─ calculateChange(montant, total) : Double
└─ generateReceipt(cmd) : String

ReportService (optionnel)
├─ generateDailyReport() : Report
├─ getTopPlats(limit) : List<Plat>
├─ getTotalRevenue() : Double
└─ getAverageOrderValue() : Double
```

**Pattern utilisé** : `executeWithTransaction()` dans chaque service
```java
private <T> T executeWithTransaction(String context, TransactionCallback<T> callback) {
    // TX Begin
    // Execute callback
    // TX Commit / Rollback
    // Error handling + logging
}
```

### 5. `controller/` - Présentation & Navigation

```java
BaseController
├─ carteService : CarteService
├─ salleService : SalleService
├─ commandeService : CommandeService
├─ cuisineService : CuisineService
├─ caisseService : CaisseService
├─ setServices(...) : void (injection)
├─ showError(title, msg) : void
├─ showInfo(title, msg) : void
└─ [Pour tous les controllers hériter]

MainController
├─ loadView(fxmlPath) : void
├─ switchToCarte() : void
├─ switchToSalle() : void
├─ switchToCuisine() : void
├─ switchToCaisse() : void
└─ [Gère navigation entre écrans]

CarteController extends BaseController
├─ loadCategories() : void
├─ loadPlats() : void
├─ handleAddCategorie() : void
├─ handleAddPlat() : void
├─ handleUpdatePlat() : void
└─ handleDeletePlat() : void

SalleController extends BaseController
├─ loadTables() : void
├─ displayTables(tables) : void
├─ handleTableClick(table) : void (créer ou voir commande)
├─ loadCommandesForTable(table) : void
├─ handleAddTable() : void
└─ updateStatus() : void

CommandeController extends BaseController
├─ loadCommande(cmd) : void
├─ loadCategories() : void
├─ loadPlats() : void
├─ handleAddPlat() : void
├─ handleRemovePlat() : void
├─ handleApplyDiscount() : void
├─ handleEnvoyerCuisine() : void
└─ updateTotals() : void

CuisineController extends BaseController
├─ setupTables() : void (4 TableViews)
├─ loadCommandes() : void
├─ startAutoRefresh() : void
├─ handleMarkReady() : void (PRÉPARATION → PRÊT)
├─ handleMarkServed() : void (PRÊT → SERVI)
├─ handleSendToPreparation() : void
└─ updateStats() : void

CaisseController extends BaseController
├─ loadCommandesToPay() : void
├─ handleEnregistrerPaiement() : void
├─ displayReceipt(cmd) : void
└─ updateTotals() : void
```

**Pattern utilisé** :
```
initialize() {
    // Configuration UI SEULEMENT (spinners, colonnes, etc)
}

@Override
setServices(...) {
    // Injection services
    loadData()  // Charger données
}
```

### 6. `views/` - Interface FXML

```
main-view.fxml
├─ MenuBar (4 boutons : Carte, Salle, Cuisine, Caisse)
└─ BorderPane central (changé par navigation)

carte-view.fxml
├─ VBox Catégories
│  ├─ ComboBox sélection
│  ├─ ListView catégories existantes
│  └─ Boutons [Ajouter], [Modifier], [Supprimer]
└─ VBox Plats
   ├─ ComboBox catégorie
   ├─ TableView plats (nom, prix, image)
   └─ Boutons [Ajouter], [Modifier], [Supprimer]

salle-view.fxml
├─ GridPane tables
│  └─ Chaque table = Rectangle VERT/ROUGE/ORANGE + numéro
├─ ListView commandes (affichage table sélectionnée)
└─ Boutons [Rafraîchir], [Libérer table]

commande-view.fxml (Dialog)
├─ SplitPane
├─ GAUCHE : ListView lignes commandes
│  ├─ Bouton [Supprimer]
│  └─ Spinner modifier quantité
└─ DROITE : ComboBox catégorie/plat + Spinner + [Ajouter]
   ├─ TextField remise + Bouton [Appliquer]
   └─ Boutons [Envoyer en cuisine], [Annuler]

cuisine-view.fxml
├─ SplitPane 4 colonnes
├─ EN_ATTENTE ──► [➡️ Envoyer en préparation]
├─ EN_PRÉPARATION ──► [✅ Marquer prêt]
├─ PRÊT ──► [🍽️ Marquer servi]
└─ SERVI (affichage seulement)

caisse-view.fxml
├─ TableView commandes SERVI
├─ TextField montant payé
├─ ComboBox mode paiement
├─ [Enregistrer paiement]
├─ Label reçu
└─ [Libérer table]
```

---

## 🔗 Relations entre classes

### Diagramme entités

```
Categorie (1) ──── (N) Plat
     │
     │
     │   Plat (1) ──────── (N) LigneCommande
     │                           │
     │                           │
     │                      (1)  │  (N) Commande
     │                           │      │
     │                           │      │
     │                           │  (1) │  (N) TableResto
     │                           │      │
     │                           │      │
     │                           │  (1) │  (N) Paiement
```

### Flot de données

```
Controller
    │
    ├─ Reçoit input utilisateur (boutons, saisie)
    │
    ▼
Service
    ├─ Valide données (ValidationException)
    ├─ Applique règles métier
    ├─ Gère transactions (TX Begin/Commit/Rollback)
    │
    ▼
DAO (GenericDAO + Spécialisés)
    ├─ Execute requête SQL
    ├─ Fetch relations (LEFT JOIN FETCH)
    ├─ Gère EntityManager
    │
    ▼
Hibernate + H2
    ├─ Exécute SQL
    └─ Retourne objets Java

Service
    ├─ Mappe résultat
    │
    ▼
Controller
    ├─ Rafraîchit UI (setText, setAll, etc)
    │
    ▼
Vue FXML
    └─ Affiche données
```

### Exemple : Créer commande

```
1. SalleController.handleTableClick(table)
        ▼
2. SalleService.startNewCommande(table)
        ▼
3. executeWithTransaction() {
        ▼
4.    CommandeDAO.save(new Commande(table, EN_ATTENTE))
        ▼
5.    Hibernate INSERT into COMMANDE
        ▼
6.    TX Commit
   }
        ▼
7. refreshAll() {
        ▼
8.    Nouvel EntityManager
        ▼
9.    SalleService.getAllTables()
        ▼
10.   TableDAO.findAll() LEFT JOIN FETCH commandes
        ▼
11.   TableResto.isOccupee() retourne true
   }
        ▼
12. Platform.runLater()
        ▼
13. SalleController.loadTables()
        ▼
14. displayTables() crée Rectangle ROUGE
        ▼
15. CommandeController.loadCommande()
        ▼
16. Fenêtre Commande ouvre
```

---

## 🔧 Spécifications techniques

### Environnement

| Composant | Version | Description |
|-----------|---------|-------------|
| Java | 17 | LTS, compilée en cible 17 |
| JavaFX | 17.0.2 | UI framework |
| Hibernate | 5.6.5.Final | ORM |
| H2 Database | 2.1.210 | BD fichier embeddée |
| Maven | 3.6+ | Build tool |
| SLF4J | 1.7.36 | Logging API |
| Logback | 1.2.11 | Logging implémentation |

### Base de données

```
Driver : org.h2.Driver
URL : jdbc:h2:./data/restaurant;AUTO_SERVER=TRUE
User : sa (admin)
Password : (vide)
Fichier : ./data/restaurant.mv.db

Stratégie DDL : update (garder données au redémarrage)
Transactions : RESOURCE_LOCAL (JDBC)
Batch insert : 20 objets
```

### Configuration Hibernate

```properties
hibernate.dialect = org.hibernate.dialect.H2Dialect
hibernate.hbm2ddl.auto = update
hibernate.show_sql = false
hibernate.format_sql = true
hibernate.jdbc.batch_size = 20
hibernate.order_inserts = true
hibernate.order_updates = true
```

### Gestion des transactions

```java
Pattern : executeWithTransaction(String context, TransactionCallback<T> callback)

Responsabilités :
├─ Begin transaction
├─ Execute callback
├─ Commit if success
├─ Rollback if error
├─ Log exceptions
└─ Throw DatabaseException
```

### Gestion des erreurs

```
Exception Hierarchy
├─ RestaurantException (root)
├─ ValidationException (données invalides)
├─ DatabaseException (problèmes BD)
└─ RuntimeException (imprévu)

ErrorLogger
├─ Écrit dans logs/restaurant.log
├─ Affiche en console
└─ Format : [HH:MM:SS] [LEVEL] Context - Message
```

### Gestion des images

```
Format : Base64 (BD) + Fichier (uploads/)
Stockage BD : imageBase64 (varchar 10000)
Stockage fichier : imagePath (varchar 255)
Créateur : ImageManager.imageToBase64(file)
Suppression : ImageManager.deleteImageFile(path)
```

### Concurrence & Temps réel

```
Cuisine Auto-Refresh
├─ ScheduledExecutorService (1 thread pool)
├─ Interval : 2 secondes
├─ Crée nouvel EntityManager chaque cycle
├─ Platform.runLater() retour UI thread
└─ ObservableLists synchronisées

Synchronisation
├─ UI thread : JavaFX EDT
├─ Refresh thread : ScheduledThreadPoolExecutor
├─ Pas de lock (BD pessimiste) = last-write-wins
└─ Acceptable pour restaurant (peu utilisateurs)
```

### Performance

| Opération | Timeout | Notes |
|-----------|---------|-------|
| Charger tables | <100ms | 1 LEFT JOIN |
| Charger commandes | <500ms | LEFT JOIN FETCH x3 |
| Créer commande | <100ms | INSERT simple |
| Calculer total | <10ms | Stream Java |
| Refresh cuisine | <500ms | 4 requêtes en parallèle (non bloquant) |

### Limitations & Futures améliorations

```
Actuellement :
├─ ✅ Single user (pas de multi-utilisateur)
├─ ✅ H2 fichier (ok pour PME, pas cloud)
├─ ✅ Pas de backup automatique
├─ ✅ Pas d'authentification
└─ ✅ Pas de chiffrement données

Futures améliorations possibles :
├─ PostgreSQL cloud
├─ Multi-utilisateur avec optimistic locking
├─ Système d'authentification (JWT/OAuth)
├─ Backup / Disaster recovery
├─ API REST (SpringBoot)
├─ Mobile app (React Native)
└─ Analytics (Kibana / Grafana)
```

---

## 📊 Schéma Base de Données

```sql
CREATE TABLE categorie (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nom VARCHAR(100) UNIQUE NOT NULL,
  description VARCHAR(500),
  dateCreation TIMESTAMP NOT NULL,
  dateModification TIMESTAMP NOT NULL
);

CREATE TABLE plat (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nom VARCHAR(100) NOT NULL,
  prix DOUBLE NOT NULL,
  description VARCHAR(500),
  categorie_id BIGINT NOT NULL,
  imageBase64 VARCHAR(10000),
  imagePath VARCHAR(255),
  dateCreation TIMESTAMP NOT NULL,
  dateModification TIMESTAMP NOT NULL,
  FOREIGN KEY (categorie_id) REFERENCES categorie(id)
);

CREATE TABLE table_resto (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  numeroTable INTEGER UNIQUE NOT NULL,
  capacite INTEGER NOT NULL,
  dateCreation TIMESTAMP NOT NULL,
  dateModification TIMESTAMP NOT NULL
);

CREATE TABLE commande (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_id BIGINT NOT NULL,
  statut VARCHAR(50) NOT NULL,
  dateCommande TIMESTAMP,
  dateServi TIMESTAMP,
  remiseAppliquee DOUBLE,
  dateCreation TIMESTAMP NOT NULL,
  dateModification TIMESTAMP NOT NULL,
  FOREIGN KEY (table
