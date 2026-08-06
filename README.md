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

## Local development with MySQL

The default application datasource is MySQL. Start the local MySQL 8.4 and Redis
containers before running the backend from an IDE or Gradle:

```bash
docker compose -f compose.yml -f compose.local.yml up -d mysql redis

cd backend
./gradlew bootRun
```

The backend connects to MySQL on `localhost:3307` and Redis on `localhost:6379`.

To run the shared Docker stack with the local MySQL override:

```bash
docker compose -f compose.yml -f compose.local.yml up -d --build
```

The local MySQL connection for MySQL Workbench is:

```text
Hostname: 127.0.0.1
Port: 3307
Username: community
Password: local-development-password
Default schema: community
```

Flyway creates and updates the local schema from `backend/src/main/resources/db/migration`.
Hibernate only validates the resulting schema and does not modify it.

## Automated tests

Automated persistence tests use a disposable MySQL 8.4 Testcontainers database.
Docker must be running, but no manually managed test database or AWS resource is required:

```bash
cd backend
./gradlew test
```

Each test database is created from the same Flyway migrations used by local MySQL
and RDS.

## Production RDS

Production runs with `SPRING_PROFILES_ACTIVE=prod` and reads `DB_URL`,
`DB_USERNAME`, and `DB_PASSWORD` from the EC2 deployment directory's `.env`.
The RDS instance should be private and allow MySQL port `3306` only from the EC2
security group. Use MySQL Workbench over an EC2 SSH tunnel to inspect it.

## Validation

```bash
cd backend
./gradlew test

cd ../frontend-react
pnpm install
pnpm lint
pnpm build
```
