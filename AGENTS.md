# AGENTS.md — GENOMETRICS

**GENOMETRICS** by Bruno Omena — local-first FASTQ/VCF quality control, packaged as an agent skill (Python, no server).

Read this file first. Keep diffs small and aligned with GitHub Flow (see below).

---

## Project map

| Area | Path | Notes |
|------|------|-------|
| Skill | `skills/genometrics/` | `SKILL.md` + `scripts/genometrics/` (Python 3.10+, no dependencies; CLI `python3 -m genometrics`) |
| Marketplace | `.claude-plugin/marketplace.json` | Lists the skill for `/plugin marketplace add` |
| Tests | `tests/` | pytest: behavior, golden parity, CLI |
| Fixtures | `datasets/` | Tiny FASTQ/VCF/FASTA samples and `golden/` (frozen output of the removed Java backend) |
| Local agent harness | `.agents/` | Not in git: maintainer rules (`rules/*.md`) and work logs / PR drafts (`work/`) for any AI agent |
| CI | `.github/workflows/ci.yml` | `skill` job (ruff + pytest) on PR/push |

### Run locally

```bash
PYTHONPATH=skills/genometrics/scripts python3 -m genometrics qc datasets/sample.fastq
pip install pytest ruff && ruff check && ruff format --check && pytest
```

- No runtime dependencies, no server, no database, no account. Nothing leaves the machine.

---

## Branches and deploy (`main` only)

Official branch: **`main`** (renamed from `prod`). Do not use legacy **`test`**.

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
3. **English only** — CLI messages, docs, commits, PR text.
4. **Reuse** — one upload flow, one metrics API shape; no duplicate parsers/DTOs FE↔BE.
5. **Tests** — add/update only for behavior you changed; run `ruff check && pytest` when touching the skill.
6. **No secrets** — never commit `.env`, keys, or real credentials.

### Before finishing

1. **Delete dead code** you replaced.
2. **Local repository: you may edit freely.** Edit files, create branches, stage explicit paths and commit on `feature/*`, `fix/*`, `hotfix/*` without asking. Do not discard work you did not make (`reset --hard`, `clean`, `stash drop`, `branch -D`) without asking.
   **Anything that leaves the machine needs Bruno's yes in the current chat, asked each time:** `git push` (every push, even to a branch already pushed) and `gh pr create`. Before asking, run `ruff check && ruff format --check && pytest`; ask only if it passes, and say which branch and commits will be published. **Never merge**, never push to `main`, never force-push or push tags, never delete/rename remote branches or change repo settings unless Bruno asks for that specific action.
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
- Keep the skill's code in `skills/genometrics/scripts/genometrics/`.

### Do not

- Wrappers/adapters for a single caller.
- Feature flags or strategy enums unless requested.
- Large unsolicited docs or comments.

---

## Stack reference

- **Skill:** Python 3.10+ standard library only; `scripts/genometrics/` (streaming FASTQ and VCF parsers, QC verdict, CLI)
- **Tests:** pytest; **lint:** ruff
- **Reference output:** `datasets/golden/` is frozen; the Java backend that produced it was removed (ADR 0001, amended)

## AI assistants

Follow [`CONTRIBUTING.md`](CONTRIBUTING.md) for branches, priorities and tests. Match the style of the nearest file. If `.agents/rules/` exists in your checkout, read and follow those rules too (any agent: Cursor, Claude Code, Codex…). That folder is the maintainer's local harness and is not part of the repository.
