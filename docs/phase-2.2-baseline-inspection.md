# Phase 2.2 baseline inspection

Inspection performed 2026-10-31 in `D:\4th Year\8th Sem\MediaCheck1` before the latest follow-up edits.

## Repository and configuration

- Existing repository layout retained: `backend` (Spring Boot, domain, auth, security, repositories, Flyway and tests), `frontend` (React/Vite auth context/pages/API client), `ai-service`, `infrastructure`, `contracts`, and `docs`.
- `.git` is absent; no Git history, tracked-file status, or historical diff can be established. `.gitignore` contains `.env` and allows `.env.example`; `.env` was not printed or scanned for secret values.
- Maven POM targets Java 21. Available runtime reported Java 22.0.2; deployment Dockerfile/runtime was not rebuilt in this inspection, so Java consistency between local and container runtime is not established.
- Compose defines backend, frontend, AI service, PostgreSQL 17, and RabbitMQ 4. Backend connects to `postgres:5432` and depends on the `rabbitmq` healthy service; PostgreSQL data uses named volume `postgres-data`. Host mappings are localhost-bound. Compose runtime/config validation could not reach the Docker Engine named pipe.
- Frontend's container build argument defaults to backend `http://localhost:8080`; `.env.example` supplies the host-facing backend URL `http://localhost:8081`. The frontend source defaults to `http://localhost:8081`.
- Backend uses Spring Boot 3.4.5, Hibernate `ddl-auto: validate`, Flyway, HS256 Nimbus JWT, database-checked active membership/session, stateless CSRF cookie, credentialed allowlisted CORS, and an in-memory auth route limiter.
- Email-provider configuration and dependencies are absent. SMTP/API credentials are not available in `.env.example` and no values were inspected from `.env`.

## Migration fingerprints

Migration source SHA-256 at inspection:

| Migration | SHA-256 |
| --- | --- |
| V1 | `10BCAD713F061D9AB28893D7AA235B91D36169BC29125D2671F0F3D608CA96D4` |
| V2 | `7DE3140DE7701C738F43AD089EAFD20B586CE797DBFB22A9DDEB64CC32E07232` |
| V3 | `46AE72BAD21922631AD6368C91D97BA9A625CA23468ED05084F3E305B674384D` |

These are source-file hashes, not Flyway database checksums. No migration was changed in this follow-up. Prior Phase 2.1 records reported PostgreSQL 17.11 with V1–V3 applied; database history was not queried here because Docker was inaccessible.

## Initial verification blockers

- `docker version` reported Docker client 29.9.2 but server access failed with permission denied on `npipe:////./pipe/docker_engine`.
- `mvn -o test` is the reproducible local test command. The Maven wrapper PowerShell launcher failed with a null-array error; system Maven 3.9.12 was used.
- npm entrypoint resolution failed under the restricted user profile path. Local TypeScript, ESLint, and Vite entrypoints ran directly with Node.
- No real browser automation runtime/package was found in `frontend/node_modules`; no browser acceptance was performed.

No project reinitialization or Git initialization was attempted. Existing local database/volumes were not touched.
