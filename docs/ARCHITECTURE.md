# Architecture du Système — E-Commerce Microservices App

> Ce document décrit l'architecture technique, les choix de conception, les composants et les flux de données du projet **E-Commerce Microservices App (`ecom-ms-app`)**.

---

## 1. Vue d'ensemble (High-Level Design)

L'application repose sur une **architecture microservices distribuée** propulsée par le framework **Spring Cloud** et un client web moderne développé en **Angular 19**. Elle adopte le principe fondamental du découpage par domaine métier (*Domain-Driven Design*) ainsi que le patron de conception **Database-per-Service**, garantissant un couplage faible, une scalabilité horizontale indépendante et une forte résilience aux pannes.

<div align="center">
  <img src="assets/02_eureka_instances_registered.png" alt="Registre des microservices dans Eureka" width="85%" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
  <p><em>Figure 1 : Enregistrement dynamique et état de santé des instances microservices sur Netflix Eureka Server</em></p>
</div>

### Topologie Globale du Système

```mermaid
flowchart TD
    User["Utilisateur / Navigateur Web"]
    
    subgraph Frontend ["Couche Présentation (SPA)"]
        Angular["Angular 19 Client\n(:4200)"]
    end

    subgraph Infrastructure ["Infrastructure & Découverte Spring Cloud"]
        Gateway["Spring Cloud Gateway\n(:8888)\n[Reverse Proxy & CORS]"]
        Eureka["Eureka Discovery Service\n(:8761)\n[Service Registry]"]
        ConfigServer["Spring Cloud Config Server\n(:9999)\n[Git Centralized Config]"]
    end

    subgraph CoreServices ["Microservices Métiers & Données"]
        CustomerMS["Customer Service\n(:8081)\n[Spring Data REST]"]
        InventoryMS["Inventory Service\n(:8082)\n[Spring Data REST]"]
        BillingMS["Billing Service\n(:8083)\n[OpenFeign + Resilience4j]"]

        DBCustomer[("H2 DB\ncustomers-db")]
        DBInventory[("H2 DB\nproducts-db")]
        DBBilling[("H2 DB\nbilling-db")]
    end

    User --> Angular
    Angular --> Gateway
    Gateway -.-> Eureka
    Gateway --> CustomerMS
    Gateway --> InventoryMS
    Gateway --> BillingMS

    CustomerMS -. Enregistrement .-> Eureka
    InventoryMS -. Enregistrement .-> Eureka
    BillingMS -. Enregistrement .-> Eureka
    Gateway -. Enregistrement .-> Eureka

    CustomerMS -. Configuration .-> ConfigServer

    BillingMS -- "OpenFeign (Synchrone)" --> CustomerMS
    BillingMS -- "OpenFeign (Synchrone)" --> InventoryMS

    CustomerMS --- DBCustomer
    InventoryMS --- DBInventory
    BillingMS --- DBBilling
```

---

## 2. Composants Principaux

| Composant | Technologie | Rôle & Responsabilités |
| :--- | :--- | :--- |
| **Frontend SPA** | `Angular 19`, `Bootstrap 5`, `RxJS` | Interface utilisateur monopage réactive : consultation des catalogues, gestion des clients, formulaire de passation et affichage détaillé des factures. |
| **API Gateway** | `Spring Cloud Gateway`, `Spring WebFlux` | Point d'entrée unique (`:8888`), gestion globale du CORS, routage statique (`/customers/**`, `/products/**`) et dynamique via Discovery Locator (`/{SERVICE-ID}/**`). |
| **Discovery Registry** | `Netflix Eureka Server` | Registre de découverte dynamique (`:8761`) assurant la localisation des services et l'équilibrage de charge côté client (*Client-Side Load Balancing*). |
| **Config Server** | `Spring Cloud Config Server` | Serveur de configuration centralisé (`:9999`) synchronisé avec un dépôt Git pour externaliser et versionner les paramètres d'environnement. |
| **Customer Service** | `Spring Boot`, `Spring Data REST`, `H2` | Gestion des clients (nom, email), persistance relationnelle, exposition HATEOAS et support du rechargement à chaud via `@RefreshScope`. |
| **Inventory Service** | `Spring Boot`, `Spring Data REST`, `H2` | Gestion du catalogue de produits et des niveaux de stock, persistance relationnelle, exposition des endpoints REST. |
| **Billing Service** | `Spring Boot`, `OpenFeign`, `Resilience4j`, `H2` | Orchestration de la facturation et des lignes d'articles (`ProductItem`), agrégation inter-services via clients déclaratifs OpenFeign et disjoncteurs de secours. |

---

## 3. Flux de données (Data Workflow)

Le diagramme suivant détaille le cycle de vie complet d'une requête complexe : **la consultation d'une facture détaillée (`GET /bills/{id}`)**, impliquant la découverte de services, la communication synchrone inter-microservices et le mécanisme de secours (*fallback*) Resilience4j :

```mermaid
sequenceDiagram
    autonumber
    actor User as Client / Navigateur
    participant UI as Angular SPA (:4200)
    participant GW as Spring Cloud Gateway (:8888)
    participant Eureka as Eureka Server (:8761)
    participant BillMS as Billing Service (:8083)
    participant CustMS as Customer Service (:8081)
    participant InvMS as Inventory Service (:8082)
    participant DB as H2 Database (billing-db)

    User->>UI: Clique sur "Détails de la facture #1"
    UI->>GW: GET /BILLING-SERVICE/bills/1
    GW->>Eureka: Résolution de l'instance "BILLING-SERVICE"
    Eureka-->>GW: IP:Port (localhost:8083)
    GW->>BillMS: GET /bills/1
    
    BillMS->>DB: SELECT * FROM Bill WHERE id = 1
    DB-->>BillMS: Données Bill (id, customerId, billingDate, productItems)
    
    rect rgb(240, 248, 255)
        note over BillMS,CustMS: Communication OpenFeign + Circuit Breaker
        BillMS->>CustMS: GET /customers/{customerId}
        alt Customer Service Disponible
            CustMS-->>BillMS: 200 OK (Customer JSON)
        else Panne ou Timeout
            BillMS->>BillMS: Resilience4j Fallback : getDefaultCustomer()
        end
    end

    rect rgb(245, 255, 245)
        note over BillMS,InvMS: Résolution des Produits par Ligne de Commande
        loop Pour chaque ProductItem
            BillMS->>InvMS: GET /products/{productId}
            alt Inventory Service Disponible
                InvMS-->>BillMS: 200 OK (Product JSON)
            else Panne ou Timeout
                BillMS->>BillMS: Resilience4j Fallback : getDefaultProduct()
            end
        end
    end

    BillMS->>BillMS: Assemblage du DTO enrichi (Bill + Customer + Products)
    BillMS-->>GW: 200 OK (Payload JSON Complet)
    GW-->>UI: 200 OK (Transfert réponse avec en-têtes CORS)
    UI-->>User: Affichage du tableau de bord de facture & calcul des totaux
```

---

## 4. Modèle de données & Stockage

Chaque microservice métier dispose de son propre schéma de base de données isolé (**Database-per-Service**), évitant tout verrouillage inter-bases direct.

### Entités Relationnelles (JPA)

```mermaid
erDiagram
    CUSTOMER {
        Long id PK
        String name
        String email
    }

    PRODUCT {
        Long id PK
        String name
        double price
        int quantity
    }

    BILL {
        Long id PK
        Date billingDate
        Long customerId "Clé logique externe"
    }

    PRODUCT_ITEM {
        Long id PK
        Long productId "Clé logique externe"
        int quantity
        double price
        Long bill_id FK
    }

    BILL ||--o{ PRODUCT_ITEM : "contient"
```

- **Customer Service (`customers-db`) :**
  - Table `CUSTOMER` : identifiant auto-généré, nom complet et adresse e-mail.
- **Inventory Service (`products-db`) :**
  - Table `PRODUCT` : identifiant auto-généré, nom du produit, prix unitaire et stock disponible.
- **Billing Service (`billing-db`) :**
  - Table `BILL` : identifiant de facture, date d'émission et référence logique `customerId`.
  - Table `PRODUCT_ITEM` : ligne d'achat associant `productId`, quantité commandée, prix unitaire au moment de la facturation et clé étrangère locale vers `BILL`.
  - Attributs `@Transient` : les objets `Customer` et `Product` sont instanciés à la volée en mémoire lors des requêtes d'agrégation.

---

## 5. Résilience, Sécurité & Déploiement

### Tolérance aux pannes avec Resilience4j
- **Circuit Breaker :** Les interfaces Feign (`CustomerServiceRestClient`, `InventoryServiceRestClient`) sont annotées avec `@CircuitBreaker`. Si le taux d'erreur dépasse le seuil paramétré ou en cas de délai d'attente dépassé (*timeout*), le disjoncteur bascule à l'état `OPEN`.
- **Méthodes Fallback :**
  - `getDefaultCustomer(Long id, Exception exception)` : retourne un profil client par défaut (`name: "default"`, `email: "default@gmail.com"`).
  - `getDefaultProduct(Long id, Exception e)` : retourne un objet produit minimal contenant l'identifiant demandé, évitant ainsi un crash de l'interface utilisateur.

<div align="center">
  <img src="assets/22_billing_resilience4j_fallback.png" alt="Fallback Resilience4j" width="85%" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
  <p><em>Figure 2 : Tolérance aux pannes — Déclenchement automatique du Fallback Resilience4j lors de l'arrêt du Customer Service</em></p>
</div>

### Sécurité & Gestion Cross-Origin (CORS)
- **Filtre Réactif :** Un bean `CorsWebFilter` configuré dans `gateway-service` intercepte toutes les requêtes entrantes pour autoriser les origines `http://localhost:4200`, `http://localhost:3000` et `http://localhost:5173` avec les méthodes HTTP `GET, POST, PUT, DELETE, PATCH, OPTIONS`.
- **Pas de proxy complexe :** L'application front-end communique de manière unifiée avec le port `8888`.

### Configuration Dynamique & Rechargement à chaud
- Externalisation des propriétés métiers (`customer.params.x`, `customer.params.y`, `global.params.p1`, etc.) dans un dépôt Git via **Spring Cloud Config Server**.
- L'annotation `@RefreshScope` sur les contrôleurs cibles permet de propager les nouvelles valeurs instantanément via un simple appel POST à l'endpoint Spring Boot Actuator (`/actuator/refresh`) sans redémarrer le microservice.

<div align="center">
  <img src="assets/07_actuator_refresh_scope.png" alt="Rechargement Actuator Refresh" width="85%" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
  <p><em>Figure 3 : Rechargement à chaud des beans Spring annotés avec @RefreshScope via Actuator</em></p>
</div>

---

[⬅ Retour au README principal](../README.md)
