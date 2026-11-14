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

- **DNS rebinding:** requests whose `Host` is not `localhost`, `127.0.0.1` or `[::1]` get `403`. Add your own names with `NGS_SECURITY_ALLOWED_HOSTS` (comma separated, no port) if you reach the API through another host name.
- **Cross-site requests:** state-changing requests (anything but GET, HEAD, OPTIONS, TRACE) that carry an `Origin` header, which includes `/mcp` and every `/api` call, are rejected unless the origin has the same host and port as the request or is listed in `NGS_SECURITY_ALLOWED_ORIGINS`. Command-line and MCP clients send no `Origin` and are not affected.
- **JWT secret:** with auth enabled the API refuses to start while `NGS_JWT_SECRET` is missing, shorter than 32 bytes or the public default.

`NGS_SECURITY_ALLOWED_HOSTS` takes host names or IPs without a port. Bare IPs other than `127.0.0.1` and `::1` (for example a LAN address when you set `SERVER_ADDRESS=0.0.0.0`) must be listed too. `NGS_SECURITY_ALLOWED_ORIGINS` only lets a request carrying that `Origin` through; the API sends no CORS headers, so a web page still cannot read cross-origin responses. Only widen these lists for hosts and origins you control.

## Reading local files from MCP (`analyze_file`)

The `analyze_file` tool lets an agent analyze a file by path. It is **off by default** and only appears once `NGS_MCP_ANALYZE_ROOTS` lists the directories it may read (comma separated). It then reads only absolute paths to regular files whose real location, after resolving symlinks and `..`, is inside one of those directories; anything else is refused. Files must have a FASTQ, VCF or FASTA name, stay under the upload size limit, and are copied into `NGS_STORAGE_LOCAL_DIR` like an upload. Blank, relative and `/` entries are ignored. Paths outside the roots all get the same error, so the tool cannot be used to find out which files exist elsewhere. With auth enabled, only `ADMIN` accounts may use it. List only directories that hold sequencing data, never your home directory, and keep them writable only by people you trust: a file swapped or grown after the check is refused, but a hard link placed inside a root cannot be told apart from a normal file.

## Deployment requirements

The defaults are for a single local machine only. Before exposing GENOMETRICS on **any** network you **must**:

- set `NGS_AUTH_DISABLED=false` (auth is disabled by default for local development);
- set a unique `NGS_JWT_SECRET` (the default is public in `backend/src/main/resources/application.yml`).

Reports that only show these defaults being insecure on an exposed instance are treated as misconfiguration, not vulnerabilities.
