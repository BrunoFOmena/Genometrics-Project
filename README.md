# NGS Analytics Platform

Local-first platform for FASTQ/VCF quality and variant metrics, with a Spring Boot API and Angular dashboards.

Designed to run on a **16 GB RAM Windows laptop** (modular monolith with streaming Java parsers).

## Stack

- **Backend:** Java 21, Spring Boot 3, JWT, JPA
- **Frontend:** Angular 19 + Apache ECharts
- **Database:** H2 (dev/tests) · PostgreSQL (prod/staging, when needed)
- **Jobs:** in-process async Java parsers · local file storage
- **Deploy (optional):** Docker image for the API (`docker/Dockerfile.api`)

## Quick start

### Prerequisites

- JDK 21, Maven 3.9+, Node 22+

### 1. Start API (H2 — default)

```bash
cd backend
mvn spring-boot:run
```

No Docker or Postgres required. Profile `dev-h2` is the default.

API: `http://localhost:8080`  
Swagger: `http://localhost:8080/swagger-ui.html`

### 2. Start UI

```bash
cd frontend
npm start
```

UI: `http://localhost:4200`

### 3. Try fixtures

1. Register a user in the UI
2. Create a project + sample
3. Upload [`datasets/sample.fastq`](datasets/sample.fastq) and [`datasets/sample.vcf`](datasets/sample.vcf)
4. Wait a few seconds for analysis status `DONE` and open charts

## PostgreSQL (prod / staging — optional)

When you need Postgres locally:

```bash
cd docker
docker compose up -d
cd ../backend
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

For cloud/staging, activate `prod` and set:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Tests

```bash
cd backend && mvn test
cd ../frontend && npm test -- --watch=false --browsers=ChromeHeadless
```

Backend tests use in-memory H2. Optional Postgres smoke (Docker): `RUN_TESTCONTAINERS=true mvn -Dtest=PostgresContainerIT test`.

## Git and CI

`develop` is development. `main` is production. Never push to either: work lands on `develop` first, then `develop` is promoted to `main`.

```bash
git checkout develop
git pull
git checkout -b feature/<name>   # or fix/<name> or hotfix/<name>
# ... commit ...
git push -u origin HEAD
```

1. Open a PR **into `develop`** from `feature/*`, `fix/*` or `hotfix/*`.
2. After CI is green and the PR is merged, open a PR **`develop` → `main`**.
3. Image GHCR (`:latest` + `:sha`) publishes only on that production merge.

| Event | Tests | GHCR production |
|-------|--------|-----------------|
| Push to `feature/*`, `fix/*`, `hotfix/*` or `develop` | Yes | No |
| PR into `develop` from those prefixes | Yes | No |
| PR into `develop` from any other branch | Gate fails | No |
| PR `develop` → `main` | Yes (required) | No until merge |
| PR into `main` from any branch other than `develop` | Gate fails | No |
| Merge `develop` → `main` after backend, frontend and e2e pass | Yes | Yes |

Protect **both** `develop` and `main` (**Settings → Rules → Rulesets**): require a pull request; require checks `from-release-line`, `backend`, `frontend` and `e2e`; block force pushes and branch deletion.

## Hardware notes

| Resource | Guidance |
|----------|----------|
| RAM | API heap ~1 GB; optional Postgres Compose ≤ 512 MB |
| Disk | Use tiny fixtures in `datasets/`; prune `data/uploads` |
| GPU | Not used |

## Default credentials (optional local Postgres)

| Service | User | Password |
|---------|------|----------|
| PostgreSQL | `ngs` | `ngs` |
