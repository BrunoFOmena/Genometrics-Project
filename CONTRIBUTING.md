# Contributing to GENOMETRICS

Thank you for your interest in contributing. This project is maintained by **Bruno Omena**; all changes land on the **`prod`** branch through pull requests.

## Branch model

| Branch | Purpose |
|--------|---------|
| **`prod`** | Official line — default on GitHub, deployable, Docker images published on push |
| **`feature/*`**, **`fix/*`**, **`hotfix/*`** | Short-lived work branches |

There is no separate integration branch. Open your PR **into `prod`**.

## How to contribute

1. Fork the repository (or ask for a collaborator branch if you already have access).
2. Sync with upstream `prod`:
   ```bash
   git fetch origin
   git checkout prod
   git pull origin prod
   git checkout -b feature/your-change
   ```
3. Make focused commits; follow [Conventional Commits](https://www.conventionalcommits.org/) in PR titles when possible.
4. Run tests locally before opening a PR:
   ```bash
   ./scripts/ci-check.sh
   ```
5. Open a **pull request targeting `prod`**. Describe what changed and how you tested it.
6. Wait for CI (`backend`, `frontend`, `e2e`) and maintainer review.

**Only the maintainer merges PRs.**

## Maintainer setup (GitHub)

Recommended branch protection on **`prod`**: require PR, status checks, restrict push/merge to maintainer, no force-push. Set **default branch** to **`prod`**.

## Releases

Merges to **`prod`** publish GHCR images (`:latest` and `:${GITHUB_SHA}`). Tagged releases use SemVer (`v0.x.y`).

## Questions

Open a GitHub Issue for bugs or feature discussions before large PRs.
