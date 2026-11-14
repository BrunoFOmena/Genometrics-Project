# Security policy

GENOMETRICS processes genomic files, which can be sensitive personal data. Please report security problems privately.

## Reporting a vulnerability

- Use **[GitHub private vulnerability reporting](https://github.com/BrunoFOmena/Genometrics-Project/security/advisories/new)**.
- **Do not** open a public issue, PR or discussion for vulnerabilities.
- **Do not** attach real patient or participant data to a report. Use the synthetic files in [`datasets/`](datasets/) to reproduce.

Include the affected version or commit, steps to reproduce and the impact you observed. You should get a first response within 7 days.

## Supported versions

Only the latest commit on `main` receives fixes (and the latest release, once tagged releases exist).

## Browser protection (local mode)

With auth off (the default), the API trusts anything that can reach `127.0.0.1`, so a web page open in your browser must not be able to talk to it:

- **DNS rebinding:** requests whose `Host` is not `localhost`, `127.0.0.1` or `[::1]` get `403`. Add your own names with `NGS_SECURITY_ALLOWED_HOSTS` (comma separated) if you reach the API through another host name.
- **Cross-site requests:** `/mcp` and state-changing `/api/**` calls that carry an `Origin` header are rejected unless the origin is the API itself or is listed in `NGS_SECURITY_ALLOWED_ORIGINS` (for example `http://localhost:3000`). Command-line and MCP clients send no `Origin` and are not affected.
- **Default JWT secret:** with auth enabled the API refuses to start while `NGS_JWT_SECRET` is the public default.

Only widen these lists for hosts and origins you control.

## Deployment requirements

The defaults are for a single local machine only. Before exposing GENOMETRICS on **any** network you **must**:

- set `NGS_AUTH_DISABLED=false` (auth is disabled by default for local development);
- set a unique `NGS_JWT_SECRET` (the default is public in `backend/src/main/resources/application.yml`);
- if you use PostgreSQL, set your own `SPRING_DATASOURCE_*` credentials.

Reports that only show these defaults being insecure on an exposed instance are treated as misconfiguration, not vulnerabilities.
