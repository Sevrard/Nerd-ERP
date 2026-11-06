# Nerd ERP — Frontend (Angular 21)

Interface web pour Nerd ERP (compta, CRM, devis, factures, paiements, maintenance).

## Prérequis

- **Node.js ≥ 20.19** ou **≥ 22.12** (obligatoire pour Angular 21)
- npm (fourni avec Node)

Le fichier `.nvmrc` pointe sur **20.19.4**. Avec [nvm](https://github.com/nvm-sh/nvm) :

```bash
cd frontend
nvm install   # une fois
nvm use       # à chaque nouveau terminal si besoin
```

## Installation

```bash
cd frontend
npm install
```

## Lancement en développement

Le serveur de dev proxyfie l’API vers le backend Spring Boot :

```bash
npm start
```

Les scripts npm (`start`, `build`, `watch`, `test`) passent par `scripts/ng.mjs`, qui bascule automatiquement sur une Node compatible installée via nvm (celle du `.nvmrc` en priorité) si ton `node` global est trop ancien, ex. 20.17 via Homebrew. Un `ng build` lancé directement avec un Node trop ancien échoue en revanche : utilise `npm run build`.

Ouvrir [http://localhost:4200/](http://localhost:4200/).

Le fichier `proxy.conf.json` redirige `/api` vers `http://localhost:8080`. L’URL de base de l’API est configurée dans `src/environments/environment.ts` :

```ts
apiBaseUrl: '/api/v1'
```

En production, `environment.prod.ts` utilise la même base relative (déployer le frontend derrière le même hôte que l’API ou adapter `apiBaseUrl`).

## Build production

```bash
npm run build
```

Artefacts dans `dist/`.

## Tests unitaires

```bash
npm test
```

## Authentification

Page `/login` : `tenantSlug`, e-mail et mot de passe. Le JWT est stocké en localStorage et injecté par `authInterceptor` sur les appels `/api/v1` (sauf login).
