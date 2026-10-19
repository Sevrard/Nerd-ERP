# Nerd ERP

Monorepo de l’ERP Nerd Group / Nerd-Forge.

```
nerd-erp/
├── backend/     # API REST Spring Boot (Java 21)
└── frontend/    # Interface utilisateur (à venir)
```

## Backend

Voir [backend/README.md](backend/README.md).

```bash
cd backend
docker compose up -d
mvn spring-boot:run
```

## Frontend

Application Angular 21 (Tailwind, thème sombre type maquette). Voir [frontend/README.md](frontend/README.md).

```bash
cd frontend
npm install
npm start
```

Prérequis : **Node.js ≥ 20.19** (recommandé : 22.12). Proxy dev vers le backend sur `/api`.
# Nerd-ERP
