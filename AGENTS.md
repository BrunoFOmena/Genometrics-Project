# AGENTS.md — GENOMETRICS

**GENOMETRICS** by Bruno Omena — local-first FASTQ/VCF analytics (Spring Boot + Angular SPA).

Read this file first. Keep diffs small and aligned with GitHub Flow (see below).

---

## Project map

| Area | Path | Notes |
|------|------|-------|
| API | `backend/` | Java 21, Spring Boot 3, modular packages under `com.ngs.analytics.*` |
| UI | `frontend/` | Angular 19 SPA, sidebar shell in `app.component.ts`, pages in `src/app/pages/` |
| Business logic | `backend/.../fastq/FastqParser.java`, `vcf/VcfParser.java`, `analytics/AnalysisService.java` | Streaming parsers + async analysis |
| Domain | `backend/.../domain/` | JPA entities and repositories |
| Fixtures | `datasets/` | Tiny FASTQ/VCF/FASTA samples for manual tests |
| Local notes (maintainer) | `Docs_Bruno/` | Not in git (personal plans and checklists) |
| CI | `.github/workflows/ci.yml` | Tests on PR/push; GHCR image on push to `prod` |

### Run locally

| Service | Command | URL |
|---------|---------|-----|
| API | `cd backend && mvn spring-boot:run` | http://localhost:8080 (Swagger `/swagger-ui.html`) |
| UI | `cd frontend && npm start` | http://localhost:4200 |

- Default DB: H2 (`dev-h2` profile). Postgres only with `prod` + Compose.
- **Auth is temporarily disabled** for dev: `ngs.auth.disabled=true` (backend), `authDisabled: true` (frontend `environment.ts`). Re-enable before production.
- H2/uploads persist in `data/` (gitignored). Delete to reset.
- No `mvnw`; use system `mvn`. Cloud Agents: no Docker — do not run Compose or `prod` profile there.

---

## Branches and deploy (`prod` only)

Official branch: **`prod`**. Do not use legacy **`main`** or **`test`**.

```
feature/*  fix/*  hotfix/*  ──► PR (Bruno merges) ──► prod
```

Rules: `CONTRIBUTING.md` and branch **`prod`** for integration.

### Workflow

1. Branch from **`prod`**: `git fetch origin && git checkout prod && git pull && git checkout -b feature/<short-name>`
2. PR **`feature/…` → `prod`**. CI green; Bruno merges.
3. Merge to **`prod`** when CI is green and the maintainer approves.

### Do not

- Push directly to **`prod`** (PR + Bruno merge only).
- Force-push **`prod`** or skip CI.

### Branch names

| Prefix | Use |
|--------|-----|
| `feature/` | New capability |
| `fix/` | Bug fix |
| `hotfix/` | Urgent production fix |

---

## AI coprogramming rules

### Before coding

1. **Scope the task** — one concern per PR/session when possible.
2. **Read nearest existing code** — extend before creating files.
3. **Check auth flags** — dev mode may hide login; do not assume JWT is required locally.

### While coding

1. **Smallest correct diff** — no drive-by refactors or new abstractions for one caller.
2. **Match conventions** — package layout, naming, patterns already in the file you edit.
3. **English only** — UI strings, API messages, docs, commits, PR text.
4. **Reuse** — one upload flow, one metrics API shape; no duplicate parsers/DTOs FE↔BE.
5. **Tests** — add/update only for behavior you changed; run `mvn test` / `npm test` when touching those areas.
6. **No secrets** — never commit `.env`, keys, or real credentials.

### Before finishing

1. **Delete dead code** you replaced.
2. **Do not commit** unless Bruno explicitly says yes. **Do not push** — Bruno runs `git push` himself.
3. **Do not edit README/AGENTS** unless the task requires it.
4. Summarize what changed, how to test, and any flags (auth, profiles).

### Token economy

| Habit | Why |
|-------|-----|
| Touch few files | Less context for the next agent |
| Prefer edit over scaffold | Less boilerplate |
| Keep public APIs stable | Avoid FE/BE cascade |
| Short comments only when non-obvious | Less noise |

---

## Coding principles (priority order)

1. **Simplicity first**
2. **Token economy**
3. **No over-engineering**
4. **No duplicate code paths**

### Do

- Extend nearest class/component before adding files.
- Colocate related logic.
- Use `backend/...` modular packages and `frontend/src/app/{core,pages}`.

### Do not

- Wrappers/adapters for a single caller.
- Feature flags or strategy enums unless requested.
- Large unsolicited docs or comments.

---

## Stack reference

- **Backend:** Java 21, Spring Boot 3, JWT (optional), JPA, in-process `@Async` jobs
- **Frontend:** Angular 19, Apache ECharts, sidebar SPA shell
- **DB:** H2 (dev/test) · PostgreSQL (prod)
- **Deploy:** `docker/Dockerfile.api` + `docker/Dockerfile.frontend` → GHCR on push to `prod`; full stack via `compose.yaml`

---

## AI assistants

Follow `CONTRIBUTING.md` for branches and tests. Match existing code style in the nearest file; English for UI strings and API messages.
