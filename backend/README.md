# Nerd ERP — Backend

Monolithe modulaire Java 21 / Spring Boot 3.3 pour la gestion multi-workspace (Nerd Group, Nerd-Forge, etc.).

## Prérequis

- Java 21
- Maven 3.9+
- Docker (PostgreSQL local et tests Testcontainers)

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

## Synchronisation Etsy (workspace Nerd-Forge)

Sans ces valeurs, le dashboard affiche *« API Etsy non configurée »*.

| Clé `application-local.yml` | Variable d’env | Description |
|-----------------------------|----------------|-------------|
| `nerd.etsy.api-key` | `ETSY_API_KEY` | **Keystring** de l’app (ou `keystring:shared_secret` en une seule valeur) |
| `nerd.etsy.shared-secret` | `ETSY_SHARED_SECRET` | **Shared secret** (obligatoire si `api-key` n’inclut pas déjà `:`) |
| `nerd.etsy.access-token` | `ETSY_ACCESS_TOKEN` | Token OAuth utilisateur (scopes **`transactions_r`**, listings) |
| `nerd.etsy.shop-id` | `ETSY_SHOP_ID` | ID numérique de la boutique |

Exemple dans `application-local.yml` (voir aussi `application-local.yml.example`) :

```yaml
nerd:
  etsy:
    api-key: …
    access-token: …
    shop-id: 12345678
```

Redémarrer le backend après modification.

### Ce que l’écran « Your Apps » donne (et ce qu’il ne donne pas)

| Etsy Developers | Rôle | YAML |
|-----------------|------|------|
| **Keystring** | Identifie l’app | `nerd.etsy.api-key` |
| **Shared secret** | Complète `x-api-key` | `nerd.etsy.shared-secret` |
| *(absent de cette page)* | Accès à **tes** commandes / listings privés | `nerd.etsy.access-token` (OAuth 2.0) |

Le **shared secret n’est pas** le `access-token`. Sans OAuth, le ping public peut marcher, pas la sync des receipts.

**Obtenir un `access-token` (une fois, ~5 min)** — [Quick Start Etsy](https://developers.etsy.com/documentation/tutorials/quickstart) :

1. Dans l’app Etsy, ajoute une **Redirect URL** `https` (ex. `http://localhost:3003/oauth/redirect` pour un petit script local).
2. Ouvre une URL du type `https://www.etsy.com/oauth/connect?...` avec `client_id=<keystring>`, `scope=transactions_r%20listings_r`, PKCE (`code_challenge` / `code_verifier`).
3. Après autorisation, échange le `code` contre un token : `POST https://api.etsy.com/v3/public/oauth/token` (`grant_type=authorization_code`, `code_verifier`, …).
4. Copie **`access_token`** (commence par ton `user_id` + `.`) dans `access-token`. Conserve **`refresh_token`** pour renouveler (durée ~1 h pour l’access token).

**Script local (recommandé)** — lit `api-key` / `shared-secret` depuis `application-local.yml` :

```bash
cd backend
node scripts/etsy-oauth.mjs
```

Étapes détaillées :

1. Sur [Your Apps](https://www.etsy.com/developers/your-apps) → app **nerd-erp** → **Redirect URI** : ajoute exactement `http://localhost:3003/oauth/redirect` (même port que le script).
2. Dans un terminal : `cd backend && node scripts/etsy-oauth.mjs`.
3. Copie l’URL longue affichée (`https://www.etsy.com/oauth/connect?...`) et ouvre-la dans le navigateur (session Etsy du vendeur).
4. Clique **Autoriser** → le navigateur revient sur localhost → le terminal affiche `access-token: …` à coller dans `application-local.yml`.
5. Redémarre `mvn spring-boot:run`.

Options : `--port 3004`, `--scopes "transactions_r listings_r"`, ou variables `ETSY_KEYSTRING` / `ETSY_SHARED_SECRET`.

**Renouveler l’access token** (expire ~1 h ; le `refresh-token` dure ~90 j) :

```bash
cd backend
node scripts/etsy-refresh-token.mjs
```

Met à jour `access-token` (et `refresh-token` si Etsy en renvoie un) dans `application-local.yml`, puis redémarre le backend. `--no-write` pour afficher sans écrire le fichier.

| Clé YAML | Description |
|----------|-------------|
| `nerd.etsy.refresh-token` | Jeton de renouvellement (sortie de `etsy-oauth.mjs`) |

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
