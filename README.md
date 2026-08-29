# GENOMETRICS

Local-first platform for FASTQ/VCF quality and variant metrics — by **Bruno Omena**.

Spring Boot API + Angular SPA dashboard.

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

1. Open the UI (auth is temporarily disabled in dev — no login required)
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

## Git and CI (GitHub Flow)

**`main`** is the only long-lived branch and must stay deployable. All work merges via pull request.

```bash
git checkout main
git pull
git checkout -b feature/<name>   # or fix/<name> or hotfix/<name>
# ... commit ...
git push -u origin HEAD
```

1. Open a PR **into `main`** from your branch.
2. Wait for CI: `backend`, `frontend`, `e2e`.
3. Merge when green. Delete the branch after merge.
4. Push to `main` publishes the GHCR API image (`:latest` + `:sha`).

| Event | Tests | GHCR image |
|-------|--------|------------|
| PR into `main` | Yes | No |
| Push to `feature/*`, `fix/*`, `hotfix/*` | Yes | No |
| Push to `main` (after merge) | Yes | Yes |

Protect **`main`** (**Settings → Rules → Rulesets**): require pull request; require checks `backend`, `frontend`, `e2e`; block force pushes and branch deletion.

AI agents: see [`AGENTS.md`](AGENTS.md) and [`.cursor/rules/`](.cursor/rules/) for project context and coprogramming rules.

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
