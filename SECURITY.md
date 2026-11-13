# Security policy

GENOMETRICS processes genomic files, which can be sensitive personal data. Please report security problems privately.

## Reporting a vulnerability

- Use **[GitHub private vulnerability reporting](https://github.com/BrunoFOmena/Genometrics-Project/security/advisories/new)**.
- **Do not** open a public issue, PR or discussion for vulnerabilities.
- **Do not** attach real patient or participant data to a report. Use the synthetic files in [`datasets/`](datasets/) to reproduce.

Include the affected version or commit, steps to reproduce and the impact you observed. You should get a first response within 7 days.

## Supported versions

Only the latest commit on `main` receives fixes (and the latest release, once tagged releases exist).

## Deployment requirements

The defaults are for a single local machine only. Before exposing GENOMETRICS on **any** network you **must**:

- set `NGS_AUTH_DISABLED=false` (auth is disabled by default for local development);
- set a unique `NGS_JWT_SECRET` (the default is public in `backend/src/main/resources/application.yml` and `docker/docker-compose.yml`);
- change the Postgres credentials in `docker/docker-compose.yml`.

Reports that only show these defaults being insecure on an exposed instance are treated as misconfiguration, not vulnerabilities.
