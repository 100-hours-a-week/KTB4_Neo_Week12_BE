# KTB4 Neo Week12 Backend

Spring Boot backend and the shared Docker Compose entry point for the community application.

## Repository layout

The frontend is maintained in a separate repository. Clone both repositories with these
directory names so the shared Compose build contexts resolve correctly:

```text
KTB4_Neo_Week12/
├── compose.yml
├── .env
├── backend/
└── frontend-react/
```

This repository is the `KTB4_Neo_Week12` directory. The backend source lives in
`backend/`, and the FE repository must be cloned into `frontend-react/`.

```bash
git clone https://github.com/100-hours-a-week/KTB4_Neo_Week12_BE.git KTB4_Neo_Week12
cd KTB4_Neo_Week12
git clone https://github.com/100-hours-a-week/KTB4_Neo_Week12_FE.git frontend-react
cp .env.example .env
```

Replace `JWT_SECRET` in `.env` before deployment.

## Local Docker test

Expose only Nginx:

```bash
docker compose up -d --build
```

Expose backend port `8080` as well:

```bash
docker compose -f compose.yml -f compose.local.yml up -d --build
```

## Validation

```bash
cd backend
./gradlew test

cd ../frontend-react
pnpm install
pnpm lint
pnpm build
```
