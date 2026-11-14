# Contributing to GENOMETRICS

Thank you for your interest in contributing. This project is maintained by **Bruno Omena** and licensed under [Apache-2.0](LICENSE); by submitting a contribution you agree it is licensed under the same terms (Apache-2.0, section 5). Please follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Data policy (read first)

GENOMETRICS handles genomic data, which is sensitive personal data.

- **Never** commit, upload or paste real patient or participant data in code, issues, PRs, logs or screenshots.
- Use the synthetic fixtures in [`datasets/`](datasets/) or public data whose license allows redistribution; keep fixtures tiny.
- GENOMETRICS is for **research use only**. It is not a medical device and must not be presented as diagnostic.
- Security problems: follow [SECURITY.md](SECURITY.md), not a public issue.

## Branch model

| Branch | Purpose |
|--------|---------|
| **`main`** | Official line: default on GitHub, deployable |
| **`feature/*`**, **`fix/*`**, **`hotfix/*`** | Short-lived work branches |

There is no separate integration branch. Open your PR **into `main`**.

## Issues, priorities and PRs

Every change starts as a GitHub issue. The maintainer gives each open issue exactly one priority label:

| Label | Meaning | Expectation |
|-------|---------|-------------|
| **P0** | Critical: broken build/CI or deploy, security or data exposure, or blocks other planned work | Handle now, before anything else |
| **P1** | High: next on the roadmap, core functionality | Current or next cycle |
| **P2** | Normal: improvements, nice-to-have, can wait | When capacity allows |

New contributors: look for [`good first issue`](https://github.com/BrunoFOmena/Genometrics-Project/labels/good%20first%20issue). Comment on an issue before starting so work is not duplicated.

Every pull request **must close an issue** with a closing keyword in the PR body, e.g. `Closes #123` (`Fixes` / `Resolves` also work). This links the PR to the issue and closes it on merge; the `PR issue link` CI check fails without it. If a PR only delivers part of an issue, split the remaining work into a new issue first.

## How to contribute

1. Fork the repository (or ask for a collaborator branch if you already have access).
2. Sync with upstream `main` and branch:
   ```bash
   git fetch origin
   git checkout main
   git pull origin main
   git checkout -b feature/your-change
   ```
3. Keep the change focused on one issue. Use [Conventional Commits](https://www.conventionalcommits.org/) in the PR title (`feat:`, `fix:`, `docs:`, `chore:` …); PRs are squash-merged.
4. Run the checks locally before opening a PR:
   ```bash
   pip install pytest ruff
   ruff check && ruff format --check && pytest
   ```
5. Open a **pull request targeting `main`** using the template (`Closes #<issue>`, summary, test plan).
6. Wait for CI (`skill`, `issue-link`) and maintainer review.

**Only the maintainer merges PRs.**

## Maintainer setup (GitHub)

- Default branch: **`main`**.
- Branch protection on **`main`** (ruleset "Protect main"): changes only through a PR, squash merge only, linear history, no force-push or deletion, required checks (`skill`, `issue-link`) on an up-to-date branch, resolved review conversations.
- Review: PRs from contributors need **1 approval from a code owner** (`.github/CODEOWNERS`), re-requested after every new push. The maintainer may merge their own PRs without a review (repository-admin bypass, pull requests only; direct pushes stay blocked).
- Private vulnerability reporting enabled (Settings → Code security).

## Releases

Tagged releases use SemVer (`v0.x.y`).

## Questions

Open a GitHub Issue for bugs or feature discussions before large PRs.
