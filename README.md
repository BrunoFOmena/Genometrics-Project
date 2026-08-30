# 🧬 GENOMETRICS — NGS QC & Metrics Platform

**Local-first platform for quality control and variant metrics on FASTQ/VCF files.**

Built by **Bruno Omena** · modular monolith designed to run on a **16 GB RAM Windows laptop**.

![Status](https://img.shields.io/badge/Status-Active-success)
![Angular](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Database](https://img.shields.io/badge/Database-H2%20%7C%20PostgreSQL-336791?logo=postgresql&logoColor=white)

---

## 📖 About the Project

**GENOMETRICS** is a **full-stack** application focused on **Next-Generation Sequencing (NGS)**. Unlike heavy pipelines that require clusters or cloud infrastructure, this project delivers a **complete analytics dashboard** running locally — with streaming Java parsers, in-process async jobs, and on-disk file storage.

The goal is to provide **immediate visibility into sequencing quality (FASTQ)** and **variant metrics (VCF)**, with upload, automatic analysis, interactive charts, and report export — all accessible through a modern SPA.

---

## ✨ Features

### 📁 Projects & Samples
- Create **projects** and **samples** organized by workspace.
- Upload **FASTQ** (single and paired-end) and **VCF** files (up to 2 GB).
- Unified file and analysis list with real-time status (`QUEUED` → `RUNNING` → `DONE`).

### 🔬 FASTQ QC
- Quality metrics: **Phred**, **GC content**, **duplication**, **N-content**.
- **Adapter** detection and **over-represented** sequences (with BLAST link).
- **Paired-end** support with automatic mate detection (`R1`/`R2`).
- **PASS / WARN / FAIL** evaluation with actionable recommendations.
- 2D heatmaps, position charts, and **Charts · Detailed · Overrepresented** tabs.

### 🧪 VCF Metrics
- Variant counts by type, quality, and chromosomal distribution.
- ECharts visualizations integrated into the sample panel.

### 📊 Dashboard & Compare
- **Overview** with animated KPIs (GSAP): projects, samples, queued and completed analyses.
- **Compare** side-by-side across samples/analyses.
- **History** with recent jobs and quick navigation.

### 📄 Reports & Export
- Download metrics as **JSON**, **CSV**, and **PDF** (FASTQ QC report).
- Swagger UI for REST API exploration.

### 🔐 Authentication *(optional in dev)*
- Login/register with JWT (Spring Security).
- **Auth disabled by default in dev** — open the UI and start immediately.

---

## 🛠️ Tech Stack

### Frontend
| Technology | Role |
|------------|------|
| **Angular 19** | Standalone SPA, routing, animations |
| **PrimeNG** | Cards, tabs, tables, chips, progress |
| **Apache ECharts** | QC and variant charts |
| **GSAP** | Animated counters on Overview |
| **Lucide Angular** | Icons |
| **Tailwind CSS** | Layout utilities |
| **TypeScript** | Static typing |
| **Cypress** | E2E tests |

### Backend
| Technology | Role |
|------------|------|
| **Java 21** | Runtime |
| **Spring Boot 3.3** | REST API, security, JPA |
| **FastqParser / VcfParser** | In-process streaming parsers |
| **SpringDoc OpenAPI** | Swagger documentation |
| **JWT (JJWT)** | Authentication |
| **H2** | Dev/test database |
| **PostgreSQL** | Prod/staging database |
| **Testcontainers** | Optional Postgres smoke tests |

### DevOps
| Technology | Role |
|------------|------|
| **GitHub Actions** | CI (`backend`, `frontend`, `e2e`) |
| **GHCR** | API Docker image on merge to `main` |
| **Docker Compose** | Local Postgres (optional) |

---

## 🚀 How to Run

### Prerequisites

- **JDK 21**
- **Maven 3.9+**
- **Node.js 22+** and npm
- *(Optional)* Docker — only for local Postgres in prod/staging

### 1. Clone the repository

```bash
git clone https://github.com/BrunoFOmena/Genometrics-Project.git
cd Genometrics-Project
```

### 2. Start the API (H2 — default)

```bash
cd backend
mvn spring-boot:run
```

No Docker or Postgres required. Profile `dev-h2` is the default.

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |

### 3. Start the UI

```bash
cd frontend
npm install
npm start
```

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:4200 |

### 4. Try the fixtures

1. Open the UI (no login required in dev).
2. Create a **project** + **sample**.
3. Upload [`datasets/sample.fastq`](datasets/sample.fastq) and [`datasets/sample.vcf`](datasets/sample.vcf) — or paired-end: [`sample_R1.fastq`](datasets/sample_R1.fastq) + [`sample_R2.fastq`](datasets/sample_R2.fastq).
4. Wait for status `DONE` and explore charts and reports.

---

## 🐘 PostgreSQL (prod / staging — optional)

```bash
cd docker
docker compose up -d
cd ../backend
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Environment variables for cloud/staging (`prod`):

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

| Service | User | Password |
|---------|------|----------|
| PostgreSQL (Compose) | `ngs` | `ngs` |

---

## 🧪 Tests

```bash
# Backend (in-memory H2)
cd backend && mvn test

# Frontend (headless)
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless

# E2E (API + UI running)
cd frontend && npm run e2e
```

Optional Postgres smoke test with Docker: `RUN_TESTCONTAINERS=true mvn -Dtest=PostgresContainerIT test`

---

## 🔀 Git & CI (GitHub Flow)

**`main`** is the only integration branch — always deployable. All work merges via **Pull Request**.

```bash
git checkout main && git pull
git checkout -b feature/<name>
# ... commits ...
git push -u origin HEAD
# Open PR → main · wait for CI · merge · delete branch
```

| Event | Tests | GHCR image |
|-------|-------|------------|
| PR → `main` | ✅ | ❌ |
| Push to `feature/*`, `fix/*`, `hotfix/*` | ✅ | ❌ |
| Push to `main` (after merge) | ✅ | ✅ |

Protect **`main`**: require PR, checks `backend` / `frontend` / `e2e`, block force-push.

AI agents: see [`AGENTS.md`](AGENTS.md) and [`.cursor/rules/`](.cursor/rules/) for project context.

---

## 💻 Hardware Notes

| Resource | Guidance |
|----------|----------|
| **RAM** | API heap ~1 GB; Postgres Compose ≤ 512 MB |
| **Disk** | Use fixtures in `datasets/`; prune `data/uploads` periodically |
| **GPU** | Not used |

---

<p align="center">
  <sub>GENOMETRICS · Bruno Omena · Local-first NGS analytics</sub>
</p>
