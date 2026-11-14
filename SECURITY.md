# Security policy

GENOMETRICS processes genomic files, which can be sensitive personal data. Please report security problems privately.

## Reporting a vulnerability

- Use **[GitHub private vulnerability reporting](https://github.com/BrunoFOmena/Genometrics-Project/security/advisories/new)**.
- **Do not** open a public issue, PR or discussion for vulnerabilities.
- **Do not** attach real patient or participant data to a report. Use the synthetic files in [`datasets/`](datasets/) to reproduce.

Include the affected version or commit, steps to reproduce and the impact you observed. You should get a first response within 7 days.

## Supported versions

Only the latest commit on `main` receives fixes (and the latest release, once tagged releases exist).

## Scope

The skill runs locally: it reads the files you name and prints JSON. It makes no network calls and does not write outside the output it prints. Reports about malformed input files (crashes, hangs or memory blow-ups) are welcome.
