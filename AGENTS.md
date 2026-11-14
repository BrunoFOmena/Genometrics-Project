# AGENTS.md — GENOMETRICS

**GENOMETRICS** by Bruno Omena — local-first FASTQ/VCF analytics exposed to AI agents via MCP (Spring Boot API, no UI).

Read this file first. Keep diffs small and aligned with GitHub Flow (see below).

---

## Project map

| Area | Path | Notes |
|------|------|-------|
| API | `backend/` | Java 21, Spring Boot 3, modular packages under `com.ngs.analytics.*` |
| MCP | `backend/.../mcp/McpController.java` | JSON-RPC 2.0 over HTTP at `/mcp` (`tools/list`, `tools/call`) |
| Business logic | `backend/.../fastq/FastqParser.java`, `vcf/VcfParser.java`, `analytics/AnalysisService.java` | Streaming parsers + async analysis |
| Domain | `backend/.../domain/` | JPA entities and repositories |
| Agent skill | `.claude/skills/genometrics/` | `SKILL.md` + `scripts/analyze.sh` (starts the API, uploads, waits, prints QC); `scripts/demo.sh` runs it on the fixtures |
| Fixtures | `datasets/` | Tiny FASTQ/VCF/FASTA samples for manual tests |
| Local agent harness | `.agents/` | Not in git: maintainer rules (`rules/*.md`) and work logs / PR drafts (`work/`) for any AI agent |
| CI | `.github/workflows/ci.yml` | Tests + demo on PR/push |

### Run locally

| Service | Command | URL |
|---------|---------|-----|
| API | `cd backend && mvn spring-boot:run` | http://localhost:8080 (Swagger `/swagger-ui.html`) |
| Demo | `./scripts/demo.sh` | Starts the API if needed, loads `datasets/`, checks MCP |

- Default DB: H2 (`dev-h2` profile). Postgres only with the `prod` profile and your own server (`SPRING_DATASOURCE_*`).
- **Auth is temporarily disabled** for dev: `ngs.auth.disabled=true`. Re-enable before exposing the API on a network.
- H2/uploads persist in `data/` (gitignored). Delete to reset.
- No `mvnw`; use system `mvn`. Nothing in this repo needs Docker. Cloud Agents: do not run the `prod` profile there.

---

## Branches and deploy (`main` only)

Official branch: **`main`** (renamed from `prod`; `prod` is now only a Spring profile). Do not use legacy **`test`**.

```
feature/*  fix/*  hotfix/*  ──► PR (Bruno merges) ──► main
```

Rules: [`CONTRIBUTING.md`](CONTRIBUTING.md) (branches, priorities, PR issue link).

### Workflow

1. Branch from **`main`**: `git fetch origin && git checkout main && git pull && git checkout -b feature/<short-name>`
2. PR **`feature/…` → `main`**. CI green; Bruno merges.
3. One PR per issue; the PR body starts with `Closes #<issue>`.

### Do not

- Push directly to **`main`** (PR + Bruno merge only).
- Force-push **`main`** or skip CI.

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
3. **English only** — API and MCP messages, docs, commits, PR text.
4. **Reuse** — one upload flow, one metrics API shape; no duplicate parsers/DTOs FE↔BE.
5. **Tests** — add/update only for behavior you changed; run `mvn test` (or `./scripts/ci-check.sh`) when touching the backend.
6. **No secrets** — never commit `.env`, keys, or real credentials.

### Before finishing

1. **Delete dead code** you replaced.
2. **Local repository: you may edit freely.** Edit files, create branches, stage explicit paths and commit on `feature/*`, `fix/*`, `hotfix/*` without asking. Do not discard work you did not make (`reset --hard`, `clean`, `stash drop`, `branch -D`) without asking.
   **Anything that leaves the machine needs Bruno's yes in the current chat, asked each time:** `git push` (every push, even to a branch already pushed) and `gh pr create`. Say which branch and commits will be published. **Never merge**, never push to `main`, never force-push or push tags, never delete/rename remote branches or change repo settings unless Bruno asks for that specific action.
3. **Do not edit README/AGENTS** unless the task requires it.
4. Summarize what changed, how to test, and any flags (auth, profiles).
5. **Issue link** — every task maps to a GitHub issue labeled `P0`/`P1`/`P2` (see `CONTRIBUTING.md`). PR drafts start with `Closes #<issue>`; CI rejects PRs without it.

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
- Use `backend/...` modular packages under `com.ngs.analytics.*`.

### Do not

- Wrappers/adapters for a single caller.
- Feature flags or strategy enums unless requested.
- Large unsolicited docs or comments.

---

## Stack reference

- **Backend:** Java 21, Spring Boot 3, JWT (optional), JPA, in-process `@Async` jobs
- **DB:** H2 (dev/test) · PostgreSQL (prod)
- **Run:** `mvn spring-boot:run` (H2). No container images are published.

---

## AI assistants

Follow [`CONTRIBUTING.md`](CONTRIBUTING.md) for branches, priorities and tests. Match the style of the nearest file. If `.agents/rules/` exists in your checkout, read and follow those rules too (any agent: Cursor, Claude Code, Codex…). That folder is the maintainer's local harness and is not part of the repository.
