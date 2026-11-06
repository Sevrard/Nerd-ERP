# Nerd ERP — Backend

Monolithe modulaire Java 21 / Spring Boot 3.3 pour la gestion multi-workspace (Nerd Group, Nerd-Forge, etc.).

## Prérequis

- Java 21
- Maven 3.9+
- Docker (PostgreSQL local et tests Testcontainers)

## Tests Maven

- **`mvn test` / `mvn package` / `mvn install`** : exécute les tests unitaires **sans** les classes `*IntegrationTest` (elles ont besoin de Docker + Testcontainers).
- **Avec intégration** : `mvn verify -Pintegration-tests` (Docker doit tourner).

## PostgreSQL local

PostgreSQL n’est exposé que sur `127.0.0.1` via Docker Compose :

```bash
docker compose up -d
```

Variables par défaut (surchargeables) :

| Variable | Défaut |
|----------|--------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/nerd_erp` |
| `DATABASE_USERNAME` | `nerd_erp` |
| `DATABASE_PASSWORD` | `nerd_erp` |
| `JWT_SECRET` | *(à changer en production, ≥ 256 bits)* |
| `SERVER_PORT` | `8080` |
| `BOOTSTRAP_ENABLED` | `true` |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@nerd.local` |
| `BOOTSTRAP_ADMIN_PASSWORD` | `Admin123!` |
| `BOOTSTRAP_TENANT_SLUG` | `nerd-group` |
| `GEMINI_API_KEY` | *(clé API Google AI — scan PDF factures)* |
| `GEMINI_MODEL` | `gemini-2.5-flash` |
| `GEMINI_ENABLED` | `true` |

Au premier démarrage, un tenant, un administrateur et deux workspaces (`nerd-group`, `nerd-forge`) sont créés si le slug n’existe pas.

## Scan PDF (Gemini)

Sans clé API, l’analyse renvoie *« configurez GEMINI_API_KEY »*.

**Option A — fichier local (recommandé en dev)**

```bash
cd backend
cp application-local.yml.example application-local.yml
# Éditez application-local.yml et remplacez COLLEZ_VOTRE_CLE_ICI
mvn spring-boot:run
```

Clé gratuite : [Google AI Studio → API keys](https://aistudio.google.com/apikey).

**Option B — variable d’environnement**

```bash
export GEMINI_API_KEY="AIza..."
mvn spring-boot:run
```

Redémarrez le backend après toute modification (le processus `spring-boot:run` en cours ne recharge pas la clé seul).

## Lancement

```bash
mvn spring-boot:run
```

- API : `http://localhost:8080`
- Swagger UI : `http://localhost:8080/swagger-ui.html`
- Santé : `http://localhost:8080/actuator/health`

## Authentification

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@nerd.local",
  "password": "Admin123!",
  "tenantSlug": "nerd-group"
}
```

Utiliser le `accessToken` en `Authorization: Bearer …` sur les routes protégées.

Sélection workspace côté frontend :

1. `GET /api/v1/auth/me` — profil + liste des workspaces
2. Passer `workspaceId` en query param sur les ressources métier

## Endpoints principaux

| Méthode | Route |
|---------|--------|
| GET | `/api/v1/workspaces` |
| GET/POST/PUT | `/api/v1/customers` |
| POST | `/api/v1/customers/{id}/archive` |
| DELETE | `/api/v1/customers/{id}` |
| GET | `/api/v1/customers/{id}/history` |
| POST | `/api/v1/customers/{id}/follow-ups` |
| GET/POST/PUT | `/api/v1/products` |
| GET/POST/PUT | `/api/v1/quotations` |
| POST | `/api/v1/quotations/{id}/send` |
| POST | `/api/v1/quotations/{id}/accept` |
| POST | `/api/v1/quotations/{id}/convert-to-invoice` |
| GET | `/api/v1/invoices` |
| GET/POST | `/api/v1/payments` |
| GET/POST/PUT | `/api/v1/maintenance-contracts` |
| GET | `/api/v1/dashboard/summary` |
| GET | `/api/v1/dashboard/upcoming-actions` |
| POST | `/api/v1/invoice-scan/analyze` (multipart PDF + `workspaceId`) |
| POST | `/api/v1/invoice-scan/commit` |
| GET | `/api/v1/documents/invoices/{id}/download` |

## Tests

```bash
mvn test
```

Les tests d’intégration PostgreSQL (Testcontainers) sont ignorés si Docker n’est pas disponible (`disabledWithoutDocker = true`).

## Architecture (hexagonale)

Point d’entrée : `com.nerd.erp.Application`

```
src/main/java/com/nerd/erp/
├── api/              # Contrôleurs REST + DTO (requêtes / réponses)
├── application/      # Cas d’usage et services métier
├── domain/           # Entités, enums, règles de domaine
└── infrastructure/   # JPA, sécurité JWT, config, bootstrap, adaptateurs
```

Découpage métier par sous-packages (`customer`, `invoice`, `quotation`, `workspace`, etc.) dans chaque couche.

## Facturation électronique

La génération de document (`document`) est un stub. L’interface `ElectronicInvoicingGateway` prévoit l’intégration future avec une plateforme agréée — **sans conformité PDP/PPF tant qu’elle n’est pas branchée**.
