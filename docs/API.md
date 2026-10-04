# Référence API — E-Commerce Microservices App

> Documentation technique complète des endpoints exposés par les microservices du projet **E-Commerce Microservices App (`ecom-ms-app`)**.

- **Passerelle API (Point d'accès recommandé) :** `http://localhost:8888`
- **Format d'échange :** `application/json` et `application/hal+json` (Spring Data REST)
- **Supervision & Registre Eureka :** `http://localhost:8761`
- **Configuration Centralisée :** `http://localhost:9999`

---

## Architecture de Routage & Adressage

Les requêtes peuvent être adressées directement au microservice concerné ou de préférence via la **Spring Cloud Gateway** (`:8888`), qui prend en charge le routage dynamique basé sur le registre Eureka et gère les politiques de sécurité CORS :

| Service | Port Direct | Route via Gateway (Statique / Dynamique) |
| :--- | :---: | :--- |
| **Customer Service** | `8081` | `http://localhost:8888/customers` ou `http://localhost:8888/CUSTOMER-SERVICE/customers` |
| **Inventory Service** | `8082` | `http://localhost:8888/products` ou `http://localhost:8888/INVENTORY-SERVICE/products` |
| **Billing Service** | `8083` | `http://localhost:8888/BILLING-SERVICE/bills` |
| **Config Service** | `9999` | `http://localhost:9999/{service-name}/{profile}` |
| **Eureka Server** | `8761` | `http://localhost:8761/eureka/apps` |

---

## 1. Microservice de Facturation (`billing-service`)

### 1.1 Consultation détaillée d'une facture complète (Agrégation OpenFeign)

Récupère une facture par son identifiant en résolvant dynamiquement les informations du client auprès du `customer-service` et les détails des produits auprès de `inventory-service`. En cas d'indisponibilité, le circuit breaker Resilience4j injecte les valeurs par défaut.

- **URL :** `/bills/{id}` (via Gateway : `/BILLING-SERVICE/bills/{id}`)
- **Méthode :** `GET`

#### Exemple de Réponse (`200 OK`) :
```json
{
  "id": 1,
  "billingDate": "2026-10-24T22:30:00.000+00:00",
  "customerId": 1,
  "productsItems": [
    {
      "id": 1,
      "productId": 1,
      "quantity": 5,
      "price": 34000.0,
      "product": {
        "id": 1,
        "name": "Computer",
        "price": 34000.0,
        "quantity": 12
      }
    },
    {
      "id": 2,
      "productId": 2,
      "quantity": 2,
      "price": 12000.0,
      "product": {
        "id": 2,
        "name": "Printer",
        "price": 12000.0,
        "quantity": 10
      }
    }
  ],
  "customer": {
    "id": 1,
    "name": "Mohamed",
    "email": "med@gmail.com"
  }
}
```

> [!NOTE]
> Si le service client est hors-ligne, le champ `customer` renvoie automatiquement le fallback Resilience4j :  
> `{"id": 1, "name": "default", "email": "default@gmail.com"}`.

---

### 1.2 Création d'une facture complète avec lignes d'articles

Crée une nouvelle facture, récupère automatiquement le prix actuel de chaque produit via `inventory-service`, persiste les lignes `ProductItem` associées et retourne la facture enrichie.

- **URL :** `/bills/full` (via Gateway : `/BILLING-SERVICE/bills/full`)
- **Méthode :** `POST`
- **En-têtes :** `Content-Type: application/json`

#### Corps de la requête (Request Body) :
```json
{
  "customerId": 2,
  "productItems": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 3,
      "quantity": 1
    }
  ]
}
```

#### Réponse de succès (`200 OK`) :
```json
{
  "id": 4,
  "billingDate": "2026-10-24T23:15:00.000+00:00",
  "customerId": 2,
  "productsItems": [
    {
      "id": 10,
      "productId": 1,
      "quantity": 2,
      "price": 34000.0,
      "product": {
        "id": 1,
        "name": "Computer",
        "price": 34000.0,
        "quantity": 12
      }
    },
    {
      "id": 11,
      "productId": 3,
      "quantity": 1,
      "price": 11000.0,
      "product": {
        "id": 3,
        "name": "Smartphone",
        "price": 11000.0,
        "quantity": 6
      }
    }
  ],
  "customer": {
    "id": 2,
    "name": "Imane",
    "email": "imane@gmail.com"
  }
}
```

---

### 1.3 Liste paginée des factures (Spring Data REST)

- **URL :** `/bills` (via Gateway : `/BILLING-SERVICE/bills`)
- **Méthode :** `GET`
- **Format :** HAL JSON

---

## 2. Microservice des Clients (`customer-service`)

### 2.1 Lister tous les clients

- **URL :** `/customers` (via Gateway : `/customers` ou `/CUSTOMER-SERVICE/customers`)
- **Méthode :** `GET`
- **Paramètres optionnels :** `page`, `size`, `sort`

#### Réponse (`200 OK`) :
```json
{
  "_embedded": {
    "customers": [
      {
        "id": 1,
        "name": "Mohamed",
        "email": "med@gmail.com",
        "_links": {
          "self": { "href": "http://localhost:8081/customers/1" },
          "customer": { "href": "http://localhost:8081/customers/1" }
        }
      },
      {
        "id": 2,
        "name": "Imane",
        "email": "imane@gmail.com",
        "_links": {
          "self": { "href": "http://localhost:8081/customers/2" },
          "customer": { "href": "http://localhost:8081/customers/2" }
        }
      }
    ]
  },
  "_links": {
    "self": { "href": "http://localhost:8081/customers" },
    "profile": { "href": "http://localhost:8081/profile/customers" }
  },
  "page": {
    "size": 20,
    "totalElements": 3,
    "totalPages": 1,
    "number": 0
  }
}
```

---

### 2.2 Obtenir un client par ID

- **URL :** `/customers/{id}`
- **Méthode :** `GET`

#### Réponse (`200 OK`) :
```json
{
  "id": 1,
  "name": "Mohamed",
  "email": "med@gmail.com",
  "_links": {
    "self": { "href": "http://localhost:8081/customers/1" },
    "customer": { "href": "http://localhost:8081/customers/1" }
  }
}
```

---

### 2.3 Créer un nouveau client

- **URL :** `/customers`
- **Méthode :** `POST`
- **Corps de la requête :**
```json
{
  "name": "Karim Bennani",
  "email": "karim.bennani@gmail.com"
}
```
- **Réponse :** `201 Created` avec l'entité créée et son URL canonique dans l'en-tête `Location`.

---

### 2.4 Endpoints de configuration & Actuator

- **`GET /testConfig1` :** Retourne les paramètres injectés par `@Value` (`p1`, `p2`).
- **`GET /testConfig2` :** Retourne l'objet typé `CustomerConfigParams` (`x`, `y`).
- **`POST /actuator/refresh` :** Déclenche le rechargement dynamique des beans `@RefreshScope` sans arrêt de service.

---

## 3. Microservice d'Inventaire (`inventory-service`)

### 3.1 Lister les produits disponibles

- **URL :** `/products` (via Gateway : `/products` ou `/INVENTORY-SERVICE/products`)
- **Méthode :** `GET`

#### Réponse (`200 OK`) :
```json
{
  "_embedded": {
    "products": [
      {
        "id": 1,
        "name": "Computer",
        "price": 34000.0,
        "quantity": 12,
        "_links": {
          "self": { "href": "http://localhost:8082/products/1" }
        }
      },
      {
        "id": 2,
        "name": "Printer",
        "price": 12000.0,
        "quantity": 10,
        "_links": {
          "self": { "href": "http://localhost:8082/products/2" }
        }
      }
    ]
  }
}
```

---

### 3.2 Obtenir un produit par ID

- **URL :** `/products/{id}`
- **Méthode :** `GET`

---

### 3.3 Ajouter un nouveau produit

- **URL :** `/products`
- **Méthode :** `POST`
- **Corps de la requête :**
```json
{
  "name": "Tablette Graphique",
  "price": 4500.0,
  "quantity": 15
}
```
- **Réponse :** `201 Created`

---

## 4. Passerelle & Supervision (`gateway-service` & Eureka)

### 4.1 Cartographie des routes Gateway (Actuator)

- **URL :** `http://localhost:8888/actuator/gateway/routes`
- **Méthode :** `GET`
- **Description :** Liste exhaustive de toutes les routes statiques et des routes découvertes dynamiquement par Spring Cloud Gateway.

---

### 4.2 Consultation du registre d'instances Eureka

- **Dashboard UI :** `http://localhost:8761`
- **API Registry XML / JSON :** `http://localhost:8761/eureka/apps` (avec en-tête `Accept: application/json`)

---

## Codes de statut et gestion des erreurs

| Code HTTP | Signification | Description |
| :--- | :--- | :--- |
| `200 OK` | Succès | La requête a été traitée avec succès et les données sont retournées. |
| `201 Created` | Ressource créée | La ressource (client, produit, facture) a été enregistrée en base. |
| `204 No Content` | Sans contenu | L'action (ex: suppression `DELETE`) a réussi sans corps de réponse. |
| `400 Bad Request` | Requête invalide | Payload JSON manquant, malformé ou non conforme au DTO attendu. |
| `404 Not Found` | Introuvable | L'identifiant demandé n'existe pas dans la base de données. |
| `500 Internal Error`| Erreur serveur | Exception non gérée ; si Feign échoue, Resilience4j évite le `500` via son fallback. |
| `503 Service Unavailable`| Service indisponible | Microservice cible arrêté et aucune instance saine enregistrée dans Eureka. |

---

[⬅ Retour au README principal](../README.md)
