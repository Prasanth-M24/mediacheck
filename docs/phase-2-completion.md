# MEDIACHECK Phase 2 completion report

**Date:** 2026-11-18

## Scope

This report covers the approved Phase 2 scope: database/domain alignment, authentication and security regression, company rights and licence registry, secure local storage, registry APIs, frontend registry workflows, and infrastructure configuration. Phase 3 media analysis, policy evaluation, reporting, and AI workflows were not started.

## Implemented

- V4 registry metadata and indexes remain additive; V1, V2, and V3 were preserved.
- Registry membership references now use `(company_id, membership_id)` composite foreign keys.
- Rights assets support company-scoped CRUD, archival, file upload, checksum metadata, and download.
- Licences support company-scoped CRUD, lifecycle status, effective expiry status, documents, and dashboard counts.
- Registry writes emit audit events with company and membership actor context.
- Registry reads are company-scoped through repository queries and the authenticated JWT company claim.
- PostgreSQL nullable registry filters use explicitly typed native parameters after integration testing found and corrected a `lower(bytea)` inference defect.
- `OWNER` and `ADMIN` can write; `OWNER`, `ADMIN`, `REVIEWER`, and `MEMBER` can read.
- Local storage rejects empty/oversized files, protects against traversal, computes SHA-256, and uses atomic replacement.
- Compose now mounts persistent `mediacheck-data` for the backend.
- Frontend supports registry dashboard metrics, search, asset/licence list and detail views, create/edit/archive/status flows, asset/document multipart upload, document listing/download/delete, and role-aware write controls.

## Verification actually performed

| Check | Result |
|---|---|
| Backend compile | PASS |
| Backend regression suite | PASS: repaired `mvnw.cmd test`, 41 tests, 0 failures, 0 errors, 5 environment-gated skips |
| Backend package | PASS: fresh `mvn package -DskipTests`, executable Spring Boot JAR produced |
| Frontend TypeScript | PASS |
| Frontend ESLint | PASS |
| Frontend Vite build | PASS |
| Frontend registry flow coverage | PASS at source/build level: dashboard, list, detail, create, edit, archive/status, upload, download and delete controls are implemented against the company-scoped APIs |
| H2 domain/auth compatibility | PASS; H2 stops at V2 because V3 is PostgreSQL-specific |
| PostgreSQL V1–V4 migration | PASS: PostgreSQL 17.11; Flyway versions 1–4 successful; V4 checksum `-297677963` |
| PostgreSQL Hibernate validation | PASS: isolated PostgreSQL V4 validation test |
| PostgreSQL auth/role regression | PASS: 21 tests, 0 failures/errors |
| PostgreSQL registry integration | PASS: asset/licence listing, upload/checksum, licence/document persistence, dashboard, cross-company rejection |
| Docker Compose rebuild/runtime | PASS: current backend/frontend images rebuilt and restarted; config validation, PostgreSQL/RabbitMQ health, backend health, service connectivity and persistent storage volume verified |
| Browser acceptance | PASS against live Compose services: registration, development verification, login, session restoration after reload, registry navigation, invitation acceptance, password reset, logout, protected-route redirect, and invalid-CSRF rejection |
| Development email capture | PASS: opt-in Compose `email` profile started Mailpit and `MailpitEmailIntegrationTest` captured verification, invitation, and reset messages; no production SMTP credentials were exposed |

## Required final acceptance commands

Run with Docker access restored, using an isolated database or schema and preserving the existing development volume:

```powershell
docker compose --env-file .env -f infrastructure/docker-compose.yml config --quiet
docker compose --env-file .env -f infrastructure/docker-compose.yml build backend frontend
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d backend frontend
```

Then verify PostgreSQL 17, Flyway V1–V4 checksums, Hibernate `ddl-auto=validate`, two-company registry isolation, file checksum round-trip, RabbitMQ health, backend health, frontend availability, and browser flows. Do not use `docker compose down -v`.

## Remaining limitations

- Live external SMTP delivery still requires provider availability verification in the target deployment; the opt-in Mailpit development path and all three message workflows are verified. This is an operational deployment prerequisite, not an unimplemented Phase 2 feature.
- The browser harness exposed that any nonstandard frontend test origin must be explicitly included in `MEDIACHECK_CORS_ORIGINS`.
- Future Phase 3 business modules still require their own company-scoped services and cross-company tests.

**Verdict: COMPLETE for the approved Phase 2 implementation and acceptance scope.**
